package controllers

sealed trait Direction

/**
 * Represent the direction
 */
object Direction {

  def apply(name: String): Direction = name.toLowerCase.trim match {
    case "north" => North
    case "east" | "across" => East
    case "south" | "down" => South
    case "west" => West
    case _ => throw new NoSuchElementException(name)
  }

}

/**
 * Represent the north
 */
case object North extends Direction

/**
 * Represent the east
 */
case object East extends Direction

/**
 * Represent the south
 */
case object South extends Direction

/**
 * Represent the west
 */
case object West extends Direction


/**
 * Represent a vector
 */
case class Vec(x: Int, y: Int) {

  def unary_- = Vec(-x, -y)

  def +(o: Vec) = Vec(x + o.x, y + o.y)
  def -(o: Vec) = Vec(x - o.x, y - o.y)
  
  def *(o: Int) = Vec(x * o, y * o)
  
}

/**
 * Represent a vector
 */
object Vec {

  val zero = Vec(0, 0)
  val eX = Vec(1, 0)
  val eY = Vec(0, 1)

  def apply(s: Int): Vec = Vec(s, s)
  
  def apply(dir: Direction): Vec = dir match {
    case North => Vec(0, -1)
    case East => Vec(1, 0)
    case South => Vec(0, 1)
    case West => Vec(-1, 0)
  }

}