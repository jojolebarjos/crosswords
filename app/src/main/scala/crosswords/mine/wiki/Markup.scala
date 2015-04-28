
package crosswords.mine.wiki

import java.io.StringReader
import org.wikimodel.wem.mediawiki.MediaWikiParser
import org.wikimodel.wem.{WikiParameter, WikiParameters, EmptyWemListener, WikiReference}
import scala.collection.convert.WrapAsScala._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Container for WikiMedia markup.
 *
 * @author Johan Berdat
 * @author Vincent Mettraux
 */
trait Markup

/**
 * Content represents inline data (i.e. text, links and scripted elements).
 */
trait Content extends Markup
case class Text(text: String) extends Content
case class Reference(link: String, label: String = "", params: List[(String, String)] = Nil) extends Content
case class Macro(name: String, params: List[(String, String)] = Nil) extends Content

/**
 * Container represents structured data (i.e. sections, paragraphs, lists).
 */
trait Container extends Markup
case class Header(lvl: Int, title: Paragraph, content: Items) extends Container
case class Items(items: List[Container]) extends Container
case class Paragraph(content: List[Content]) extends Container
case class Definition(paragraph: Paragraph) extends Container
case class Quotation(paragraph: Paragraph) extends Container
case class MacroBlock(mac: Macro) extends Container

/**
 * Helper to create and print markup objects.
 */
object Markup {

  /**
   * Parse specified text into markup structures.
   */
  def apply(str: String): Container = {
    val listener = new MarkupWemListener()
    val parser = new MediaWikiParser()
    parser.parse(new StringReader(str), listener)
    listener.build()
  }

  def prettyPrint(param: (String, String)): String =
    param._1 + "='" + param._2 + "'"
  
  def prettyPrint(params: List[(String, String)]): String =
    params.map(prettyPrint).mkString(" ")

  def prettyPrint(markup: Content): String = markup match {
    case Text(text) =>
      text
    case Reference(link, "", Nil) =>
      "[[" + link + "]]"
    case Reference(link, label, Nil) =>
      "[[" + link + "(" + label + ")]]"
    case Reference(link, "", params) =>
      "[[" + link + " " + prettyPrint(params) + "]]"
    case Reference(link, label, params) =>
      "[[" + link + " (" + label + ") " + prettyPrint(params) + "]]"
    case Macro(name, Nil) =>
      "{{" + name + "}}"
    case Macro(name, params) =>
      "{{" + name + " " + prettyPrint(params) + "}}"
    case _ =>
      throw new IllegalArgumentException()
  }

  def prettyPrint(markup: Container, level: Int): String = markup match {
    case Header(lvl, title, content) =>
      val text = prettyPrint(content, level)
      val header = "\t" * level + "=" * lvl + prettyPrint(title) + "=" * lvl
      if (text.isEmpty) header else header + "\r\n" + text
    case Items(items) =>
      items.map(prettyPrint(_, level + 1)).mkString("\r\n")
    case Paragraph(content) =>
      "\t" * level + content.map(prettyPrint).mkString
    case Definition(paragraph) =>
      "\t" * level + "> " + prettyPrint(paragraph, 0)
    case Quotation(paragraph) =>
      "\t" * level + ">> " + prettyPrint(paragraph, 0)
    case MacroBlock(mac) =>
      "\t" * level + "{" + prettyPrint(mac) + "}"
    case _ =>
      throw new IllegalArgumentException()
  }

  def prettyPrint(markup: Markup): String = markup match {
    case content: Content =>
      prettyPrint(content)
    case container: Container =>
      prettyPrint(container, 0)
    case _ =>
      throw new IllegalArgumentException()
  }

}

/**
 * Builder for Markup structures.
 */
class MarkupBuilder {

  // Prepare accumulators
  private val textBuffer = new StringBuilder()
  private val contentBuffer = new ArrayBuffer[Content]()
  private val stack = new mutable.Stack[(Int, Paragraph, ArrayBuffer[Container])]()
  private var inHeader = 0

  // Add root object
  stack.push((0, null, new ArrayBuffer[Container]()))

  // Use (and clear) text accumulator to create a text content
  private def flushText() {
    if (textBuffer.nonEmpty) {
      contentBuffer += Text(textBuffer.toString())
      textBuffer.clear()
    }
  }

  // Use (and clear) content accumulator to create a paragraph
  private def finalizeParagraph() = {
    flushText()
    val result = Paragraph(contentBuffer.toList)
    contentBuffer.clear()
    result
  }

  // Convert specified triplet into a container
  private def compute(elem: (Int, Paragraph, ArrayBuffer[Container])): Container = {
    if (elem._1 >= 0)
      Header(elem._1, elem._2, Items(elem._3.toList))
    else if (elem._1 == -1)
      Items(elem._3.toList)
    else
      throw new IllegalArgumentException()
  }

  // Get current level (lists do not affect level)
  private def level =
    stack.find(_._1 >= 0).get._1

  // Close one container
  private def close() {
    val elem = compute(stack.pop())
    stack.top._3 += elem
  }

  // Close until specified level is reached
  private def close(lvl: Int) {
    require(lvl > 0)
    while (lvl <= level)
      close()
  }

  /**
   * Add specified text to current accumulator.
   */
  def addContent(text: String) {
    textBuffer.append(text)
  }

  /**
   * Add specified content to current accumulator.
   */
  def addContent(content: Content) {
    content match {
      case Text(text) =>
        addContent(text)
      case _ =>
        flushText()
        contentBuffer += content
    }
  }

  /**
   * Add specified container as a child of current container.
   */
  def addContainer(container: Container) {
    require(inHeader == 0)
    flushAsParagraph()
    stack.top._3 += container
  }

  /**
   * Start a new header with specified level.
   */
  def beginHeader(lvl: Int) {
    require(inHeader == 0)
    require(lvl > 0)
    flushAsParagraph()
    close(lvl)
    inHeader = lvl
  }

  /**
   * End current header and begin associated content.
   */
  def endHeader() {
    require(inHeader > 0)
    val elem = (inHeader, finalizeParagraph(), new ArrayBuffer[Container]())
    stack.push(elem)
    inHeader = 0
  }

  /**
   * Begin a sequence of <code>Container</code>.
   */
  def beginItems() {
    require(inHeader == 0)
    flushAsParagraph()
    val elem = (-1, null, new ArrayBuffer[Container]())
    stack.push(elem)
  }

  /**
   * End a sequence of <code>Container</code>.
   */
  def endItems() {
    require(inHeader == 0)
    require(stack.top._1 == -1)
    flushAsParagraph()
    close()
  }

  /**
   * Finalize current sequence of <code>Content</code> as a <code>Paragraph</code>.
   */
  def flushAsParagraph() {
    require(inHeader == 0)
    val paragraph = finalizeParagraph()
    if (paragraph.content.nonEmpty)
      stack.top._3 += paragraph
  }

  /**
   * Finalize current sequence of <code>Content</code> as a <code>Definition</code>.
   */
  def flushAsDefinition() {
    require(inHeader == 0)
    val paragraph = finalizeParagraph()
    if (paragraph.content.nonEmpty)
      stack.top._3 += Definition(paragraph)
  }

  /**
   * Finalize current sequence of <code>Content</code> as a <code>Quotation</code>.
   */
  def flushAsQuotation() {
    require(inHeader == 0)
    val paragraph = finalizeParagraph()
    if (paragraph.content.nonEmpty)
      stack.top._3 += Quotation(paragraph)
  }

  /**
   * Finalize current object. This builder is not longer valid.
   */
  def build(): Container = {
    require(inHeader == 0)
    flushAsParagraph()
    while (stack.size > 1)
      close()
    Items(stack.top._3.toList)
  }

}

/**
 * This Wikimedia listener build a Markup hierarchy.
 */
class MarkupWemListener extends EmptyWemListener {

  // Use builder to ease the construction
  private val builder = new MarkupBuilder()

  // Handle text
  override def onSpace(str: String) = builder.addContent(str)
  override def onEscape(str: String) = builder.addContent(str)
  override def onSpecialSymbol(str: String) = builder.addContent(str)
  override def onWord(str: String) = builder.addContent(str)
  override def onLineBreak() = builder.addContent("\r\n")
  override def onNewLine() = builder.addContent("\r\n")
  override def onEmptyLines(count: Int) = builder.addContent("\r\n" * count)
  // TODO verbatim
  override def onVerbatimBlock(str: String, params: WikiParameters) {
    // This method is called to notify about not-interpreted in-line sequence of
    // characters which should be represented in the final text "as is".
  }
  override def onVerbatimInline(str: String, params: WikiParameters) {
    // This method notifies about a verbatim (pre-formatted) block defined in the text
  }

  // Handle references
  override def onReference(ref: WikiReference) =
    builder.addContent(Reference(ref.getLink, if (ref.getLabel != null) ref.getLabel else "", toScala(ref.getParameters)))
  override def onReference(ref: String) =
    builder.addContent(Reference(ref))

  // Handle macros, extensions and properties
  override def onMacroInline(name: String, params: WikiParameters, content: String) =
    builder.addContent(Macro(name, toScala(params)))
  override def onMacroBlock(name: String, params: WikiParameters, content: String) =
    builder.addContainer(MacroBlock(Macro(name, toScala(params))))
  override def onExtensionInline(name: String, params: WikiParameters) = onMacroInline(name, params, "")
  override def onExtensionBlock(name: String, params: WikiParameters) = onMacroBlock(name, params, "")
  // TODO is it correct to handle extensions like macros?
  // TODO properties?

  // Handle headers
  override def beginHeader(level: Int, params: WikiParameters) = builder.beginHeader(level)
  override def endHeader(level: Int, params: WikiParameters) = builder.endHeader()

  // Handle lists
  override def beginList(params: WikiParameters, ordered: Boolean) = builder.beginItems()
  override def endList(params: WikiParameters, ordered: Boolean) = builder.endItems()
  override def beginDefinitionList(params: WikiParameters) = builder.beginItems()
  override def endDefinitionList(params: WikiParameters) = builder.endItems()
  override def beginQuotation(params: WikiParameters) = builder.beginItems()
  override def endQuotation(params: WikiParameters) = builder.endItems()

  // Handle end of contexts
  override def endParagraph(params: WikiParameters) = builder.flushAsParagraph()
  override def endInfoBlock(infoType: String, params: WikiParameters) = builder.flushAsParagraph() // TODO info blocks?
  override def endListItem() = builder.flushAsParagraph()
  override def endDefinitionDescription() = builder.flushAsDefinition()
  override def endDefinitionTerm() = builder.flushAsDefinition()
  override def endQuotationLine() = builder.flushAsQuotation()

  // Handle tables
  // TODO handle tables
  override def beginTable(params: WikiParameters) {}
  override def endTable(params: WikiParameters) {}
  override def beginTableRow(params: WikiParameters) {}
  override def endTableRow(params: WikiParameters) {}
  override def beginTableCell(tableHead: Boolean, params: WikiParameters) {}
  override def endTableCell(tableHead: Boolean, params: WikiParameters) {}
  override def onTableCaption(str: String) {}

  // Convert Wikimodel structures to Scala types
  def toScala(param: WikiParameter): (String, String) =
    param.getKey -> param.getValue
  def toScala(params: WikiParameters): List[(String, String)] =
    if (params == null) Nil else asScalaIterator(params.iterator()).map(toScala).toList

  // Finalize construction
  def build() = builder.build()

}
