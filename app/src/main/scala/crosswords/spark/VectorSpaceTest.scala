package crosswords.spark

import java.io.{File, BufferedWriter, FileWriter}
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import JsonInputFormat._
import play.api.libs.json.JsObject
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.feature.{HashingTF, IDF, Normalizer, IDFModel}
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import java.lang.Math
import scala.io.Source


object VectorSpaceTest {
  
  def norm(v: Vector): Double = Math.sqrt(v.toArray.map(x => x*x).sum)
  
  def dotProduct(v1: Vector, v2: Vector): Double = v1.toArray.zip(v2.toArray).map(x => x._1*x._2).sum
  
  def cosSimilarity(v1: Vector, v2: Vector): Double = dotProduct(v1, v2)/(norm(v1)*norm(v2))
  
  
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
    val indexedWordsDef = words.zipWithUniqueId.map(x => (x._2, x._1)) // RDD[(Long, (String, String))]
    val definitions = indexedWordsDef.map(x => (x._1, Stem.clean(x._2._2))) // RDD[(Long, Seq[String])]
    
    val numberOfWords = definitions.flatMap(_._2).distinct.count
    println(">> # = "+numberOfWords)
    
    // For a good mapping of the term-frequency hashing function, the number of words of the whole dictionary need to be estimated
	//val vocabularySizeEstim: Int = 1000
	val hashingTF = new HashingTF(numberOfWords.toInt)
	val tf: RDD[Vector] = hashingTF.transform(definitions.values)
	val idf: IDFModel = new IDF().fit(tf)
	tf.cache
	val tfidf: RDD[Vector] = idf.transform(tf)
	val definitionsTFIDF = definitions.zip(tfidf).map(x => (x._1._1, x._2)) // RDD[(Long, Vector)]
	
	// The normalizer normalizes the vectors, this way the cosine similarity of two vectors is just their dot product
	val normalizer = new Normalizer()
	val definitionsTFIDFNormed = definitionsTFIDF.mapValues(normalizer.transform) // RDD[(Long, Vector)]
  	
  	print(">> Enter a query: ")
  	for (line <- Source.stdin.getLines) {

  	val tmp: RDD[Vector] = sc.parallelize(Array(hashingTF.transform(Stem.clean(line))))
	val query = normalizer.transform(idf.transform(tmp)).first
	
	val sims = definitionsTFIDFNormed.map(x => (x._1, dotProduct(query, x._2))) // RDD[(Long, Double)]
	
	// Sorting is performed first locally
	val k = 10
	val partialTopK = sims.mapPartitions(it => {
             val a = it.toArray
             a.sortBy(-_._2).take(k).iterator
           }, true)
    
    val collectedTopK = partialTopK.collect
    
    val topK = collectedTopK.sortBy(-_._2).take(k) // RDD[(Long, Double)]
	
	// Printing the top-k results
	val collectedWords = indexedWordsDef.collect.toMap // Map[(Long -> (String, String))]
	println(">> Query = "+line)
	for (x <- topK) {
		println
		println("Sim = "+x._2)
		val wordDef = collectedWords.get(x._1) match {
      		case Some(s) => s
      		case None => ("?", "?")
    	}
    	println("Word = "+wordDef._1)
		println("Def = "+wordDef._2)
	}
    }
	sc.stop
  }
}



