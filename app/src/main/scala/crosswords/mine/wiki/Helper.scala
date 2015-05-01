
package crosswords.mine.wiki

/**
 * Helper functions to manipulate markup content.
 *
 * @author Johan Berdat
 */
object Helper {

  /**
   * Iterate over all headers.
   */
  def headers(markup: Markup, subheader: Boolean = true): Seq[Header] = markup match {
    case h: Header =>
      Seq(h) ++ (if (subheader) headers(h.content, subheader) else Seq.empty)
    case i: Items =>
      i.items.flatMap(headers(_, subheader))
    case _ =>
      Seq.empty
  }
  
  /**
   * Get all references.
   */
  def references(markup: Markup, subheader: Boolean = true): Seq[Reference] = markup match {
    case r: Reference =>
      Seq(r)
    case h: Header if subheader =>
      references(h.title, subheader) ++ references(h.content, subheader)
    case i: Items =>
      i.items.flatMap(i => references(i, subheader))
    case p: Paragraph =>
      p.content.flatMap(i => references(i, subheader))
    case d: Definition =>
      references(d.paragraph, subheader)
    case q: Quotation =>
      references(q.paragraph, subheader)
    case _ =>
      Seq.empty
  }

  /**
   * Get all macro blocks.
   */
  def macroBlocks(markup: Markup, subheader: Boolean = true): Seq[MacroBlock] = markup match {
    case Header(_, _, content) if subheader =>
      macroBlocks(content, subheader)
    case Items(items) =>
      items.flatMap(macroBlocks(_, subheader))
    case m: MacroBlock =>
      Seq(m)
    case _ =>
      Seq.empty
  }


  /**
   * Convert this parameter to string.
   */
  def toString(param: (String, String)): String =
    param._1 + "='" + param._2 + "'"

  /**
   * Convert these parameter to string
   */
  def toString(params: List[(String, String)]): String =
    params.map(toString).mkString(" ")

  /**
   * Convert this content to string
   */
  def toString(markup: Content): String = markup match {
    case Text(text) =>
      text
    case Reference(link, "", Nil) =>
      "[[" + link + "]]"
    case Reference(link, label, Nil) =>
      "[[" + link + "(" + label + ")]]"
    case Reference(link, "", params) =>
      "[[" + link + " " + toString(params) + "]]"
    case Reference(link, label, params) =>
      "[[" + link + " (" + label + ") " + toString(params) + "]]"
    case Macro(name, Nil) =>
      "{{" + name + "}}"
    case Macro(name, params) =>
      "{{" + name + " " + toString(params) + "}}"
    case _ =>
      throw new IllegalArgumentException()
  }

  /**
   * Convert this container to string.
   */
  def toString(markup: Container, level: Int): String = markup match {
    case Header(lvl, title, content) =>
      val text = toString(content, level)
      val header = "\t" * level + "=" * lvl + toString(title) + "=" * lvl
      if (text.isEmpty) header else header + "\r\n" + text
    case Items(items) =>
      items.map(toString(_, level + 1)).mkString("\r\n")
    case Paragraph(content) =>
      "\t" * level + content.map(toString).mkString
    case Definition(paragraph) =>
      "\t" * level + "> " + toString(paragraph, 0)
    case Quotation(paragraph) =>
      "\t" * level + ">> " + toString(paragraph, 0)
    case MacroBlock(mac) =>
      "\t" * level + "{" + toString(mac) + "}"
    case _ =>
      throw new IllegalArgumentException()
  }

  /**
   * Convert this markup structure to string.
   */
  def toString(markup: Markup): String = markup match {
    case content: Content =>
      toString(content)
    case container: Container =>
      toString(container, 0)
    case _ =>
      throw new IllegalArgumentException()
  }

}
