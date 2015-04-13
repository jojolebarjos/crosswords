
package crosswords.mine.wiki

import java.io.StringReader

import org.wikimodel.wem.mediawiki.MediaWikiParser
import org.wikimodel.wem.{WikiParameters, EmptyWemListener, WikiReference}
import scala.collection.convert.WrapAsScala
import scala.collection.immutable.TreeMap
import scala.collection.{SortedMap, mutable}
import scala.collection.mutable.ArrayBuffer

/**
 * Container for WikiMedia markup.
 *
 * @author Johan Berdat
 * @author Vincent Mettraux
 */
trait Markup

object Markup {

  def apply(str: String): Group = {
    val listener = new MarkupWemListener()
    val parser = new MediaWikiParser()
    parser.parse(new StringReader(str), listener)
    listener.build()
  }

  def prettyPrint(markup: Content): String = markup.toString

  def prettyPrint(markup: Group, level: Int): String = markup match {
    case Paragraph(content) =>
      "\t" * level + content.map(prettyPrint).mkString
    case Items(items) =>
      items.map(prettyPrint(_, level + 1)).mkString("\r\n")
    case Header(lvl, title, content) =>
      val text = prettyPrint(content, level)
      val header = "\t" * level + "=" * lvl + prettyPrint(title) + "=" * lvl
      if (text.isEmpty) header else header + "\r\n" + text
    case MacroGroup(mac, group) =>
      "\t" * level + "{" + prettyPrint(mac) + "}\r\n" + prettyPrint(group, level + 1)
    case Definition(paragraph) =>
      "\t" * level + ">" + prettyPrint(paragraph, 0)
    case _ =>
      throw new IllegalArgumentException()
  }

  def prettyPrint(markup: Markup): String = markup match {
    case content: Content => prettyPrint(content)
    case group: Group => prettyPrint(group, 0)
    case _ => throw new IllegalArgumentException()
  }

}

trait Content extends Markup
case class Text(text: String) extends Content {
  override def toString = text
}
case class Reference(ref: String) extends Content {
  override def toString = "[[" + ref + "]]"
}
case class Macro(name: String, args: SortedMap[String, String]) extends Content {
  override def toString = (name :: args.map(a => a._1 + "=" + a._2).toList).mkString("{{", "|", "}}")
}

trait Group extends Markup
case class Paragraph(content: List[Content]) extends Group {
  override def toString = content.mkString
}
case class Items(items: List[Group]) extends Group
case class Header(lvl: Int, title: Paragraph, content: Items) extends Group
case class MacroGroup(mac: Macro, group: Group) extends Group
case class Definition(paragraph: Paragraph) extends Group

class MarkupBuilder {

  private val textBuffer = new StringBuilder()
  private val contentBuffer = new ArrayBuffer[Content]()
  private val stack = new mutable.Stack[(Int, Paragraph, ArrayBuffer[Group])]()
  private var inHeader = 0
  stack.push((0, null, new ArrayBuffer[Group]()))

  private def flushText() {
    if (textBuffer.nonEmpty) {
      contentBuffer += Text(textBuffer.toString())
      textBuffer.clear()
    }
  }

  def addText(text: String) {
    textBuffer.append(text)
  }

  def addReference(ref: String) { // TODO how to store reference?
    flushText()
    contentBuffer += Reference(ref)
  }

  def addMacro(name: String, args: SortedMap[String, String]) {
    flushText()
    contentBuffer += Macro(name, args)
  }

  private def finalizeParagraph() = {
    flushText()
    val result = Paragraph(contentBuffer.toList)
    contentBuffer.clear()
    result
  }

  private def compute(elem: (Int, Paragraph, ArrayBuffer[Group])): Group = {
    if (elem._1 >= 0)
      Header(elem._1, elem._2, Items(elem._3.toList))
    else if (elem._1 == -1)
      Items(elem._3.toList)
    else
      throw new IllegalArgumentException()
  }

  private def level =
    stack.find(_._1 >= 0).get._1

  private def close() {
    val elem = compute(stack.pop())
    stack.top._3 += elem
  }

  private def close(lvl: Int) {
    require(lvl > 0)
    while (lvl <= level)
      close()
  }

  def beginHeader(lvl: Int) {
    require(inHeader == 0)
    require(lvl > 0)
    flushAsParagraph()
    close(lvl)
    inHeader = lvl
  }

  def endHeader() {
    require(inHeader > 0)
    val elem = (inHeader, finalizeParagraph(), new ArrayBuffer[Group]())
    stack.push(elem)
    inHeader = 0
  }

  def beginItems() {
    require(inHeader == 0)
    val elem = (-1, null, new ArrayBuffer[Group]())
    stack.push(elem)
  }

  def endItems() {
    require(inHeader == 0)
    require(stack.top._1 == -1)
    flushAsParagraph()
    close()
  }

  def flushAsParagraph() {
    require(inHeader == 0)
    val paragraph = finalizeParagraph()
    if (paragraph.content.nonEmpty)
      stack.top._3 += paragraph
  }

  def flushAsDefinition() {
    require(inHeader == 0)
    val paragraph = finalizeParagraph()
    if (paragraph.content.nonEmpty)
      stack.top._3 += Definition(paragraph)
  }

  def openMacro(name: String, args: SortedMap[String, String]) {
    require(inHeader == 0)
    // TODO macro blocks
  }

  def build(): Group = {
    flushAsParagraph()
    while (stack.size > 1)
      close()
    Items(stack.top._3.toList)
  }

}

class MarkupWemListener extends EmptyWemListener {

  private val builder = new MarkupBuilder()

  protected def toSortedMap(params: WikiParameters): SortedMap[String, String] =
    TreeMap.empty[String, String] ++ WrapAsScala.asScalaIterator(params.iterator()).map(p => p.getKey -> p.getValue)

  override def onSpace(str: String) {
    builder.addText(str)
  }

  override def onEscape(str: String) {
    builder.addText(str)
  }

  override def onSpecialSymbol(str: String) {
    builder.addText(str)
  }

  override def onWord(str: String) {
    builder.addText(str)
  }

  override def onLineBreak() {
    builder.addText("\r\n")
  }

  override def onNewLine() {
    builder.addText("\r\n")
  }

  override def onEmptyLines(count: Int) {
    builder.addText("\r\n" * count)
  }

  override def onReference(ref: WikiReference) {
    builder.addReference(ref.getLink) // TODO onReference
  }

  override def onReference(ref: String) {
    builder.addReference(ref)
  }

  override def onMacroInline(name: String, params: WikiParameters, content: String) {
    builder.addMacro(name, toSortedMap(params))
  }

  override def beginHeader(level: Int, params: WikiParameters) {
    builder.beginHeader(level)
  }

  override def endHeader(level: Int, params: WikiParameters) {
    builder.endHeader()
  }

  override def beginList(params: WikiParameters, ordered: Boolean) {
    builder.beginItems()
  }

  override def endList(params: WikiParameters, ordered: Boolean) {
    builder.endItems()
  }

  override def beginDefinitionList(params: WikiParameters) {
    builder.beginItems()
  }

  override def endDefinitionList(params: WikiParameters) {
    builder.endItems()
  }

  override def endParagraph(params: WikiParameters) {
    builder.flushAsParagraph()
  }

  override def endListItem() {
    builder.flushAsParagraph()
  }

  override def endDefinitionDescription() {
    builder.flushAsDefinition()
  }

  override def onMacroBlock(name: String, params: WikiParameters, content: String) {
    builder.openMacro(name, toSortedMap(params))
  }

  // TODO verbatim?
  // TODO tables?
  // TODO quotations?
  // TODO definition terms?
  // TODO info block? property block? extension?

  def build() = builder.build()

}
