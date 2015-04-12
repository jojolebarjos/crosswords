
package crosswords.util

/**
 * Helper to parallelize computations
 *
 * @author Johan Berdat
 */
object Parallel {

  private class Pool[A](val iterator: Iterator[A], val count: Int) {

    private def consume(): Option[A] = synchronized {
      if (iterator.hasNext)
        Some(iterator.next())
      else
        None
    }

    class It extends Iterator[A] {
      var pending: Option[A] = null
      override def hasNext = {
        if (pending == null)
          pending = consume()
        pending.isDefined
      }
      override def next() = {
        if (pending == null)
          pending = consume()
        val result = pending.get
        pending = consume()
        result
      }
    }

    val iterators = (1 to count).map(_ => new It())

  }

  /**
   * Create multiple concurrent iterators from a single source.
   * Order is not guaranteed.
   */
  def split[A](iterator: Iterator[A], count: Int = Runtime.getRuntime.availableProcessors()): Seq[Iterator[A]] =
    if (count <= 1) Seq(iterator) else new Pool(iterator, count).iterators

}
