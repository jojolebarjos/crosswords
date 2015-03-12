
package crosswords.util

// TODO add conversion to Vec

/**
 * @author Johan Berdat
 */
sealed trait Direction

object Direction {

  def apply(name: String): Direction = name.toLowerCase.trim match {
    case "north" => North
    case "east" | "across" => East
    case "south" | "down" => South
    case "west" => West
    case _ => throw new NoSuchElementException(name)
  }

  def fromString(name: String): Option[Direction] = try {
    Option(Direction(name))
  } catch {
    case e:NoSuchElementException => None
  }

}

case object North extends Direction

case object East extends Direction

case object South extends Direction

case object West extends Direction
