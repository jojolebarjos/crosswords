
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

/**
 * Helper to handle progression.
 */
class Progress(val max: Int) {

  private var cur = 0
  private val start = time

  private def time = System.currentTimeMillis() * 0.001

  def advance(inc: Int) {
    synchronized {
      cur += inc
    }
  }

  def progress = current * 100.0f / max

  def current = synchronized(cur)
  def remaining = math.max(0, max - current)

  def currentTime = time - start
  def remainingTime = remaining * currentTime / current

  private def format(seconds: Double): String = {
    if (seconds < 1)
      return "00:00:00"
    if (seconds > 3600 * 24)
      return "##:##:##"
    val h = (seconds / 3600).toInt
    val m = ((seconds - h * 3600) / 60).toInt
    val s = (seconds % 60).toInt
    "%02d:%02d:%02d".format(h, m, s)
  }

  override def toString = "%.2f%% - %s".format(progress, format(remainingTime))

}