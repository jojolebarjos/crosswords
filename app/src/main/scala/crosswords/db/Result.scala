
package crosswords.db

import java.sql.Statement

/**
 * Experimental wrapper for SQL ResultSet.
 * This iterator must be closed after usage.
 *
 * @author Johan Berdat
 */
class Result(private val statement: Statement, private val sql: String) extends Iterator[List[Option[String]]] {

  private val set = statement.executeQuery(sql)
  private val meta = try set.getMetaData catch { case _: Exception => null }

  private val columnCount = if (meta == null) 0 else meta.getColumnCount
  private val columnIds = (1 to columnCount).toList
  val columns = columnIds.map(meta.getColumnLabel)

  private var has = if (columnCount > 0) set.next() else false
  if (!has) close()

  override def hasNext = has
  override def next() = {
    val value = columnIds.map(i => {
      val value = set.getString(i)
      if (value == null)
        None
      else
        Some(value.trim())
    })
    has = set.next()
    if (!has) close()
    value
  }

  def close() {
    has = false
    statement.close()
  }

  private def cast[A](s: Option[String])(implicit m: Manifest[A]): A = {
    val cls = m.runtimeClass
    (
      if (cls == classOf[String]) s.getOrElse("")
      else if (cls == classOf[Int]) s.getOrElse("0").toInt
      else if (cls == classOf[Long]) s.getOrElse("0").toLong
      else if (cls == classOf[BigInt]) BigInt(s.getOrElse("0"))
      else if (cls == classOf[Float]) s.getOrElse("0").toFloat
      else if (cls == classOf[Double]) s.getOrElse("0").toDouble
      else if (cls == classOf[BigDecimal]) BigDecimal(s.getOrElse("0"))
      else throw new Exception("Failed to cast to " + m.runtimeClass + "!")
      ).asInstanceOf[A]
  }

  private def at(l: List[Option[String]], i: Int): Option[String] = if (i < l.length) l(i) else None

  def as1[A](implicit ma: Manifest[A]): Iterator[A] =
    map(l => cast[A](at(l, 0)))
  def as2[A, B](implicit ma: Manifest[A], mb: Manifest[B]): Iterator[(A, B)] =
    map(l => (cast[A](at(l, 0)), cast[B](at(l, 1))))
  def as3[A, B, C](implicit ma: Manifest[A], mb: Manifest[B], mc: Manifest[C]): Iterator[(A, B, C)] =
    map(l => (cast[A](at(l, 0)), cast[B](at(l, 1)), cast[C](at(l, 2))))
  def as4[A, B, C, D](implicit ma: Manifest[A], mb: Manifest[B], mc: Manifest[C], md: Manifest[D]): Iterator[(A, B, C, D)] =
    map(l => (cast[A](at(l, 0)), cast[B](at(l, 1)), cast[C](at(l, 2)), cast[D](at(l, 3))))
  def as5[A, B, C, D, E](implicit ma: Manifest[A], mb: Manifest[B], mc: Manifest[C], md: Manifest[D], me: Manifest[E]): Iterator[(A, B, C, D, E)] =
    map(l => (cast[A](at(l, 0)), cast[B](at(l, 1)), cast[C](at(l, 2)), cast[D](at(l, 3)), cast[E](at(l, 4))))
  def as6[A, B, C, D, E, F](implicit ma: Manifest[A], mb: Manifest[B], mc: Manifest[C], md: Manifest[D], me: Manifest[E], mf: Manifest[F]): Iterator[(A, B, C, D, E, F)] =
    map(l => (cast[A](at(l, 0)), cast[B](at(l, 1)), cast[C](at(l, 2)), cast[D](at(l, 3)), cast[E](at(l, 4)), cast[F](at(l, 5))))
  def as7[A, B, C, D, E, F, G](implicit ma: Manifest[A], mb: Manifest[B], mc: Manifest[C], md: Manifest[D], me: Manifest[E], mf: Manifest[F], mg: Manifest[G]): Iterator[(A, B, C, D, E, F, G)] =
    map(l => (cast[A](at(l, 0)), cast[B](at(l, 1)), cast[C](at(l, 2)), cast[D](at(l, 3)), cast[E](at(l, 4)), cast[F](at(l, 5)), cast[G](at(l, 6))))
  def as8[A, B, C, D, E, F, G, H](implicit ma: Manifest[A], mb: Manifest[B], mc: Manifest[C], md: Manifest[D], me: Manifest[E], mf: Manifest[F], mg: Manifest[G], mh: Manifest[H]): Iterator[(A, B, C, D, E, F, G, H)] =
    map(l => (cast[A](at(l, 0)), cast[B](at(l, 1)), cast[C](at(l, 2)), cast[D](at(l, 3)), cast[E](at(l, 4)), cast[F](at(l, 5)), cast[G](at(l, 6)), cast[H](at(l, 7))))

  def asList: Iterator[List[String]] =
    map(l => l.map(cast[String]))
  
}
