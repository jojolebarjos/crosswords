
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
  def headers(markup: Markup): Seq[Header] = markup match {
    case h: Header =>
      Seq(h) ++ headers(h.content)
    case i: Items =>
      i.items.flatMap(headers)
    case _ =>
      Seq.empty
  }

  /**
   * Find headers.
   */
  def headers(markup: Markup, pred: Header => Boolean): Seq[Header] =
    headers(markup).filter(pred).toSeq

  /**
   * Find header.
   */
  def headers(markup: Markup, title: String, level: Int = 0): Seq[Header] =
    headers(markup, h => (level == 0 || level == h.lvl) && h.title.toString.trim.toLowerCase == title.toLowerCase)

  /**
   * Get all references
   */
  def references(markup: Markup, subheader: Boolean): Seq[Reference] = markup match {
    case r: Reference =>
      Seq(r)
    case p: Paragraph =>
      p.content.flatMap(i => references(i, subheader))
    case i: Items =>
      i.items.flatMap(i => references(i, subheader))
    case h: Header if subheader =>
      references(h.title, subheader) ++ references(h.content, subheader)
    case d: Definition =>
      references(d.paragraph, subheader)
    case _ =>
      Seq.empty
  }

  /**
   * Get all references
   */
  def references(markup: Markup): Seq[Reference] =
    references(markup, true)

  /**
   * Get all definitions.
   */
  def definitions(markup: Markup, subheader: Boolean): Seq[Definition] = markup match {
    case d: Definition =>
      Seq(d)
    case i: Items =>
      i.items.flatMap(i => definitions(i, subheader))
    case h: Header if subheader =>
      definitions(h.content, subheader)
    case _ =>
      Seq.empty
  }

  /**
   * Get all definitions.
   */
  def definitions(markup: Markup): Seq[Definition] =
    definitions(markup, true)

  def expand(markup: Content, mac: Macro => String, ref: Reference => String): String = markup match {
    case m: Macro => mac(m)
    case r: Reference => ref(r)
    case _ => markup.toString
  }

  // TODO macro/reference expansion

}
