package crosswords.spark

import org.apache.spark.mllib.linalg.{SparseVector, Vector}

/**
 * Utility methods to manipulate MLlib Vector and SparseVector
 *
 * @author Laurent Valette
 */
object SparkUtil {
  def dotProductSparse(v1: SparseVector, v2: SparseVector): Double = {
    // TODO: Since indices are in increasing order, we could decrease the time complexity of this method if needed
    val products = for {
      index1 <- v1.indices
      index2 <- v2.indices
      if index1 == index2
    } yield v1(index1) * v2(index2)
    products.sum
  }

  def normSparse(v: SparseVector): Double = Math.sqrt(v.values.foldLeft(0.0) { case (accu, value) => accu + value * value })

  def cosSimilaritySparse(v1: SparseVector, v2: SparseVector): Double = dotProductSparse(v1, v2) / (normSparse(v1) * normSparse(v2))

  def multiplySparse(s: Float, v: SparseVector): SparseVector = new SparseVector(v.size, v.indices, v.values.map(value => value * s))

  def addSparse(v1: SparseVector, v2: SparseVector): SparseVector = {
    // Zip each value with its index
    val indexedValues = v1.indices.zip(v1.values) ++ v2.indices.zip(v2.values)
    // Reduce by index to compute the sum
    val sumValues = indexedValues.groupBy(_._1).map { case (index, l) => l.reduce((accu, value) => (accu._1, accu._2 + value._2)) }.toArray
    // Sort to ensure that indexes are ordered
    val sortedValues = sumValues.sortBy(indexedValue => indexedValue._1).unzip
    new SparseVector(v1.size, sortedValues._1.toArray, sortedValues._2.toArray)
  }

  def dotProduct(v1: Vector, v2: Vector): Double = v1.toArray.zip(v2.toArray).map(x => x._1 * x._2).sum

  def norm(v: Vector): Double = Math.sqrt(v.toArray.map(x => x * x).sum)

  def cosSimilarity(v1: Vector, v2: Vector): Double = dotProduct(v1, v2) / (norm(v1) * norm(v2))
}
