
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
    case m: MacroGroup =>
      headers(m.group)
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
   * Iterate over all references
   */
  def references(markup: Markup): Seq[Reference] = markup match {
    case r: Reference =>
      Seq(r)
    case p: Paragraph =>
      p.content.flatMap(references)
    case i: Items =>
      i.items.flatMap(references)
    case h: Header =>
      references(h.title) ++ references(h.content)
    case m: MacroGroup =>
      references(m.group)
    case d: Definition =>
      references(d.paragraph)
    case _ =>
      Seq.empty
  }

  // TODO macro/reference expansion

}
