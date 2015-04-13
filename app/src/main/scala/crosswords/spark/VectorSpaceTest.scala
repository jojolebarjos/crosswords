package crosswords.spark

import java.io.{File, BufferedWriter, FileWriter}
import org.apache.spark.SparkContext
import JsonInputFormat._
import play.api.libs.json.JsObject
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.feature.{HashingTF, IDF, Normalizer}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import java.lang.Math


object VectorSpaceTest {

  def myprint(s: Any) = println("\n>> "+s+" <<\n")
  
  def norm(v: Vector) = Math.sqrt(v.toArray.map(x => x*x).sum)
  
  def dotProduct(v1: Vector, v2: Vector) = v1.toArray.zip(v2.toArray).map(x => x._1*x._2).sum
  
  def cosSimilarity(v1: Vector, v2: Vector) = dotProduct(v1, v2)/(norm(v1)*norm(v2))
  
  
  /**
  * This is an example of implementation of Vector Space Retrieval. Sequences of words (e.g. dictionary definitions) are
  *	transormed in vectors using the tf-idf technique as a weighting factor.
  *	The goal is to find the top-k similar definitions for a query definition.
  *	
  *	The still are some problems or limitations like:
  *	  - Terms are assumed to be independent (problem for semantic similarity)
  *	  - Definitions have a small number of words
  *	  
  *	Of course the input definitons need to be cleaned from stop-words.
  *
  * @author Matteo Filipponi
  */  
  def main(args: Array[String]) {

    val sc = new SparkContext("local", "shell")

    val crosswords = sc.jsObjectFile("../data/crosswords/*").map(_._2)
    
    val words = Bags.clues(crosswords).distinct // RDD[(String, String)]
    //val indexedWords = words.zipWithUniqueId.map(x => (x._2, x._1)) // RDD[(Long, (String, String))]
    
    val definitions = words.map(x => x._2.split(" ").toSeq) // RDD[Seq[String]]
    //val indexedDefinitions = indexedWords.map(x => (x._1, x._2._2.split(" ").toSeq)) // RDD[(Long, Seq[String])]
    
    // For a good mapping of the term-frequency hashing function, the number of words of the whole dictionary need to be estimated
    val vocabularySizeEstim: Int = 10000
    val hashingTF = new HashingTF(vocabularySizeEstim)
	val tf: RDD[Vector] = hashingTF.transform(definitions)
	val idf = new IDF().fit(tf)
	tf.cache
	val tfidf: RDD[Vector] = idf.transform(tf)
	
	// For this example the query is the first definition of the corpus
	val query = tfidf.first
	
	// The normalizer normalizes the vectors, this way the cosine similarity of two vectors is just their dot product
	val normalizer = new Normalizer()
	val tfidfNormed = tfidf.map(normalizer.transform)
	
	val sims = tfidfNormed.map(v => dotProduct(query, v)) // RDD[Double]
	val indexedSims = sims.zipWithIndex // (similarity, index): RDD[(Double, Long)]
	
	// Sorting is performed first locally
	val k = 10
	val partialTopK = indexedSims.mapPartitions(it => {
             val a = it.toArray
             a.sortBy(-_._1).take(k).iterator
           }, true)
    
    val collectedTopK = partialTopK.collect
    
    val topK = collectedTopK.sortBy(-_._1).take(k)
	
	// Printing the top-k results
	val collectedWords = words.collect
	for (c <- topK) myprint(collectedWords(c._2.toInt)._2)
    
	sc.stop
  }	
}
