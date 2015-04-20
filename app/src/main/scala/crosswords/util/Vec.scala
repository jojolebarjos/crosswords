
package crosswords.util

import org.apache.spark.mllib.linalg.SparseVector
import org.apache.spark.mllib.linalg.Vector

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

  def dotProductSparse(v1: SparseVector, v2: SparseVector): Double = {
    // TODO: Since indices are in increasing order, we could decrease the time complexity of this method if needed
    val products = for {
      index1 <- v1.indices
      index2 <- v2.indices
      if index1 == index2
    } yield v1(index1) * v2(index2)
    products.sum
  }
  def normSparse(v: SparseVector): Double = Math.sqrt(v.values.foldLeft(0.0){case (accu, value) => accu + value * value})
  def cosSimilaritySparse(v1: SparseVector, v2: SparseVector): Double = dotProductSparse(v1, v2) / (normSparse(v1) * normSparse(v2))
  def multiplySparse(s: Float, v: SparseVector): SparseVector = new SparseVector(v.size, v.indices, v.values.map(value => value * s))
  def addSparse(v1: SparseVector, v2: SparseVector): SparseVector = {
    // Zip each value with its index
    val indexedValues = v1.indices.zip(v1.values) ++ v2.indices.zip(v2.values)
    // Reduce by index to compute the sum
    val sumValues = indexedValues.groupBy(_._1).map{case (index, l) => l.reduce((accu, value) => (accu._1, accu._2 + value._2))}.toArray
    // Sort to ensure that indexes are ordered
    val sortedValues = sumValues.sortBy(indexedValue => indexedValue._1).unzip
    new SparseVector(v1.size, sortedValues._1.toArray, sortedValues._2.toArray)

    /*val indices: ArrayBuffer[Int] = ArrayBuffer()
    val values: ArrayBuffer[Double] = ArrayBuffer()

    var takeFrom1 = false
    var takeFrom2 = false
    val iter = v1.indices.iterator :: v2.indices.iterator :: Nil
    var currentIndex = iter(0) :: iter(1) :: Nil
    do {
      // Add element with lowest index
      if (currentIndex(0) == currentIndex(1)) {
        indices.append(i1)
        values.append(v1(i1) + v2(i2))
        takeFrom1 = true
        takeFrom2 = true
      } else if (i1 < i2) {
        indices.append(i1)
        values.append(v1(i1))
        takeFrom1 = true
      } else if (i1 > i2) {
        indices.append(i2)
        values.append(v2(i2))
        takeFrom2 = true
      }

      if (takeFrom1) {
        if (it1.hasNext) {
          i1 = it1.next()
          takeFrom1 = false
        } else {
          // append rest of v2
        }
      }
      if (takeFrom2) {
        if (it2.hasNext) {
          i2 = it2.next()
          takeFrom2 = false
        } else {

        }
      }
    } while(true)

    def consume(from: Int): Boolean = {
      indices.append(currentIndex(from))
    }*/
  }

  def dotProduct(v1: Vector, v2: Vector): Double = v1.toArray.zip(v2.toArray).map(x => x._1 * x._2).sum
  def norm(v: Vector): Double = Math.sqrt(v.toArray.map(x => x*x).sum)
  def cosSimilarity(v1: Vector, v2: Vector): Double = dotProduct(v1, v2)/(norm(v1)*norm(v2))
}