
package crosswords.util

/**
 * @author Johan Berdat
 */
case class Vec(x: Int, y: Int) {

  def unary_+ = this
  def unary_- = Vec(-x, -y)

  def +(o: Vec) = Vec(x + o.x, y + o.y)
  def +(o: Int) = Vec(x + o, y + o)

  def -(o: Vec) = Vec(x - o.x, y - o.y)
  def -(o: Int) = Vec(x - o, y - o)

  def *(o: Vec) = Vec(x * o.x, y * o.y)
  def *(o: Int) = Vec(x * o, y * o)

  def /(o: Vec) = Vec(x / o.x, y / o.y)
  def /(o: Int) = Vec(x / o, y / o)

  def %(o: Vec) = Vec(x % o.x, y % o.y)
  def %(o: Int) = Vec(x % o, y % o)

  def dot(o: Vec) = x * o.x + y * o.y
  def dot(o: Int) = x * o + y * o

  def det(o: Vec) = x * o.y - y * o.x
  def det(o: Int) = x * o - y * o

  def max(o: Vec) = Vec(math.max(x, o.x), math.max(y, o.y))
  def max(o: Int) = Vec(math.max(x, o), math.max(y, o))

  def min(o: Vec) = Vec(math.min(x, o.x), math.min(y, o.y))
  def min(o: Int) = Vec(math.min(x, o), math.min(y, o))

  def sum = x + y
  def length = math.abs(x) + math.abs(y)
  def max = math.max(x, y)
  def min = math.min(x, y)

  def clockwise = Vec(-y, x)
  def counterclockwise = Vec(y, -x)

}

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