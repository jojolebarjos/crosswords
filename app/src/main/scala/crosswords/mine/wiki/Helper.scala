
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
   * Get all macros (excluding macro blocks).
   */
  def macros(markup: Markup, subheader: Boolean = true): Seq[Macro] = markup match {
    case m: Macro =>
      Seq(m)
    case h: Header if subheader =>
      macros(h.title, subheader) ++ macros(h.content, subheader)
    case i: Items =>
      i.items.flatMap(i => macros(i, subheader))
    case p: Paragraph =>
      p.content.flatMap(i => macros(i, subheader))
    case d: Definition =>
      macros(d.paragraph, subheader)
    case q: Quotation =>
      macros(q.paragraph, subheader)
    case _ =>
      Seq.empty
  }

  /**
   * Get all macro blocks.
   */
  def macroBlocks(markup: Markup, subheader: Boolean = true): Seq[Macro] = markup match {
    case Header(_, _, content) if subheader =>
      macroBlocks(content, subheader)
    case Items(items) =>
      items.flatMap(macroBlocks(_, subheader))
    case MacroBlock(mac) =>
      Seq(mac)
    case _ =>
      Seq.empty
  }

  /**
   * Get all paragraphs, except the ones from definitions and quotations.
   */
  def paragraphs(markup: Markup, subheader: Boolean = true): Seq[Paragraph] = markup match {
    case Header(_, _, content) if subheader =>
      paragraphs(content, subheader)
    case Items(items) =>
      items.flatMap(paragraphs(_, subheader))
    case p: Paragraph =>
      Seq(p)
    case Definition(p) =>
      Seq(p)
    case Quotation(p) =>
      Seq(p)
    case _ =>
      Seq.empty
  }

  /**
   * Get all definitions.
   */
  def definitions(markup: Markup, subheader: Boolean = true): Seq[Paragraph] = markup match {
    case Header(_, _, content) if subheader =>
      definitions(content, subheader)
    case Items(items) =>
      items.flatMap(definitions(_, subheader))
    case Definition(p) =>
      Seq(p)
    case _ =>
      Seq.empty
  }

  /**
   * Get all quotations.
   */
  def quotations(markup: Markup, subheader: Boolean = true): Seq[Paragraph] = markup match {
    case Header(_, _, content) if subheader =>
      quotations(content, subheader)
    case Items(items) =>
      items.flatMap(quotations(_, subheader))
    case Quotation(p) =>
      Seq(p)
    case _ =>
      Seq.empty
  }

  /**
   * Remove items of depth larger than threshold.
   */
  def limitItemsDepth[A <: Markup](markup: A, depth: Int): A = markup match {
    case Items(_) if depth > 0 =>
      Items(Nil).asInstanceOf[A]
    case Items(items) =>
      Items(items.map(limitItemsDepth(_, depth - 1))).asInstanceOf[A]
    case _ =>
      markup
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

  def toRawString(content: List[Content]): String =
    content.map{
      case Macro(_, _) => ""
      case Reference(link, _, _) => link
      case Text(text) => text
    }.mkString.trim

}
