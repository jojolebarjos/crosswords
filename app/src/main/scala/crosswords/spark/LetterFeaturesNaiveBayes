package crosswords.spark

import java.io.File

import crosswords.spark.JsonInputFormat._
import crosswords.spark.Dictionary._
import org.apache.hadoop.io.compress.BZip2Codec
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import play.api.libs.json.JsObject
import org.apache.spark.SparkConf
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.linalg.Vectors
import java.lang.Math
import org.apache.spark.mllib.classification.SVMWithSGD
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.classification.{LogisticRegressionWithLBFGS, LogisticRegressionModel}
import org.apache.spark.mllib.classification
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.tree.DecisionTree
import org.apache.spark.mllib.tree.configuration.Strategy
import org.apache.spark.mllib.tree.configuration.Algo
import org.apache.spark.mllib.tree.configuration.Algo.Classification
import org.apache.spark.mllib.tree.impurity.Gini
import org.apache.spark.mllib.tree.configuration.QuantileStrategy._
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg.Matrix
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.feature.IDF
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.rdd.DoubleRDDFunctions

import scala.io.Source

/**
 * @author Laurent Valette
 * @author Utku Sirin
 */
object LetterFeaturesNaiveBayes {
  // TODO: Use black magic to adjust the category weights
  private val CLUES_WEIGHT = 1.0f
  private val DEFS_WEIGHT = 0.6f
  private val EXAMPLES_WEIGHT = 0.6f
  private val EQUIV_WEIGHT = 0.4f
  private val ASSO_WEIGHT = 0.1f

  private val CLEANED_PARTITIONS_COUNT = 20

  private val TOPTFIDF = 1000

  def main(args: Array[String]) {
    // replace the two lines below with the line below the two lines -- since this is in cluster, not in local machine
    //System.setProperty("hadoop.home.dir", "C:/winutils/")

    //val context = new SparkContext("local[8]", "shell")

    val conf = new SparkConf()
             .setAppName("crosswords")
//           .set("spark.executor.memory", "50g")
//           .set("spark.driver.memory","50g")
//           .set("spark.cores.max","20")
//           .set("spark.storage.memoryFraction", "0.2")
//           .set("spark.task.maxFailures","4")
//           .set("spark.yarn.executor.memoryOverhead", "80")
//           .set("spark.driver.maxResultSize","50g");
    val context = new SparkContext(conf)

    //val crosswords = context.jsObjectFile("../data/crosswords/*.bz2").map(_._2)
    //val words = context.jsObjectFile("../data/definitions/*.bz2").map(_._2)
    //clean(crosswords, words, "../data/cleaned")


    // comment-in/out the below code to do the feature extraction

    println("Loading the cleaned data...");

    // get the full data
    val cleanData = loadCleaned("cleanedAll", context)

    // get a small sample
    //val cleanData = loadCleaned("cleanedSmall", context)

    println("Setting the weights...")
    val weightedData = weightCategories(cleanData)

    // reduce the number of samples
    //println("randomly sampling to reduce the number of samples...")
    //val temp = weightedDataTemp.randomSplit(Array(0.90,0.10), seed = 11L);
    //val weightedData = temp(0);

    println("Building the edges...")
    val edges = buildEdges(weightedData)

    println("Building the dictionary...")
    val dict = createDictionary(edges)

    println("extracting the features...")
    val data = extractFeatures(weightedData, dict)
    data.saveAsTextFile("letterfeature")

    println("Starting the machine learning process...")
    var iter = 0
    var auROC = 0.0
    val numIter = 1
    while(iter < numIter) {
        // Naive Bayes classifier...

        // Split data into training and test sets
        val splits = data.randomSplit(Array(0.8, 0.2), seed = 11L)
        val training = splits(0)
        val test = splits(1)
        val model = NaiveBayes.train(training, lambda = 1.0)

        val predictionAndLabel = test.map(p => (model.predict(p.features), p.label))
        val accuracy = 1.0 * predictionAndLabel.filter(x => x._1 == x._2).count() / test.count()

        println("accuracy of iteration #" + iter + ":" + accuracy)
        iter = iter + 1
    }

    context.stop()
  }

  /**
   * WARNING: This function takes a lot of time to execute! It also does not support multiple workers due to a limitation
   * of the Stemmer.
   * Clean the crossword and wiktionary definitions and save the results in the specified directory.
   * The results are saved in csv-like format where all the elements before the dash '-' compose the expression and all the
   * elements after it compose the definition.
   * @param crosswordEntries A collection of crosswords
   * @param wikiEntries A collection of wiktionary articles
   * @param outputDirectory The path (with no ending file separator) where to write the results
   * @see crosswords.spark.Stem
   */
  def clean(crosswordEntries: RDD[JsObject], wikiEntries: RDD[JsObject], outputDirectory: String): Unit = {
    def cleanToCSV(t: (String, String)): String = {
      val words = Stem.clean(t._1).mkString(",")
      val defs = Stem.clean(t._2).mkString(",")
      if (words.equals("") || defs.equals("")) {
        ""
      } else {
        words + "-" + defs
      }
    }

    val clues = Bags.clues(crosswordEntries).cache()
    val defs = Bags.definitions(wikiEntries).cache()
    val examples = Bags.examples(wikiEntries).cache()
    val equiv = Bags.equivalents(wikiEntries).cache()
    val asso = Bags.associated(wikiEntries).cache()

    // Build stem to unstem index
    val unstemVocabulary = (clues ++ defs ++ examples ++ equiv ++ asso).flatMap(t => t._1.split(" +") ++ t._2.split(" +"))
    val distinctUnstem = unstemVocabulary.distinct().coalesce(CLEANED_PARTITIONS_COUNT)
    val stem2Unstem = distinctUnstem.map(word => Stem.reduce(Stem.normalize(word)) + "," + word)
    stem2Unstem.saveAsTextFile(outputDirectory + File.separator + "stem2unstem", classOf[BZip2Codec])

    // Clean data
    val cleanClues = clues.map(cleanToCSV).filter(s => !s.equals(""))
    val cleanDefs = defs.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanExamples = examples.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanEquiv = equiv.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)
    val cleanAsso = asso.map(cleanToCSV).filter(s => !s.equals("")).coalesce(CLEANED_PARTITIONS_COUNT)

    cleanClues.saveAsTextFile(outputDirectory + File.separator + "clues", classOf[BZip2Codec])
    cleanDefs.saveAsTextFile(outputDirectory + File.separator + "defs", classOf[BZip2Codec])
    cleanExamples.saveAsTextFile(outputDirectory + File.separator + "examples", classOf[BZip2Codec])
    cleanEquiv.saveAsTextFile(outputDirectory + File.separator + "equiv", classOf[BZip2Codec])
    cleanAsso.saveAsTextFile(outputDirectory + File.separator + "asso", classOf[BZip2Codec])
  }

  /**
   * Load the cleaned crosswords and wiktionary entries
   * @param inputDirectory The path (with no ending file separator) where to read the previous results
   * @param context The Spark context
   * @return A sequence of RDD containing the cleaned data from the clues, definitions, examples, equivalents and associated
   */
  def loadCleaned(inputDirectory: String, context: SparkContext): Seq[RDD[(Seq[String], Seq[String])]] = {
    def parseCSV(s: String): (Seq[String], Seq[String]) = {
      val args = s.split("-")
      (args(0).split(","), args(1).split(","))
    }

    val clues = context.textFile(inputDirectory + File.separator + "clues/part-*").map(parseCSV)
    // first only use the crosswords data -- utku
    /*
    val defs = context.textFile(inputDirectory + File.separator + "defs/part-*").map(parseCSV)
    val examples = context.textFile(inputDirectory + File.separator + "examples/part-*").map(parseCSV)
    val equiv = context.textFile(inputDirectory + File.separator + "equiv/part-*").map(parseCSV)
    val asso = context.textFile(inputDirectory + File.separator + "asso/part-*").map(parseCSV)
    Seq(clues, defs, examples, equiv, asso)
    */
    Seq(clues)

  }

  /**
   * Weight each category and group all the collections together.
   * @param l A sequence of RDD containing the cleaned data from the clues, definitions, examples, equivalents and associated
   * @return A union of all the collections after adding the category weights.
   */
  def weightCategories(l: Seq[RDD[(Seq[String], Seq[String])]]): RDD[(Seq[String], Seq[String], Float)] = {
    // first only use the crosswords data -- utku
    l(0).map(t => (t._1, t._2, 1.toFloat))
/*  ++
      l(1).map(t => (t._1, t._2, DEFS_WEIGHT)) ++
      l(2).map(t => (t._1, t._2, EXAMPLES_WEIGHT)) ++
      l(3).map(t => (t._1, t._2, EQUIV_WEIGHT)) ++
      l(4).map(t => (t._1, t._2, ASSO_WEIGHT))
*/
  }

  /**
   * Build the feature vectors for each word based on the frequency of each letter in its definition
   * @param extracted An RDD where each element is a word/sentence, its definition and the category weight
   * @return An RDD where each element is a feature vector
   */
  def extractFeatures(weightedData: RDD[(Seq[String], Seq[String], Float)], dict: RDD[(Long, String)]): RDD[LabeledPoint] = {
    val revDict = Dictionary.swap(dict).collectAsMap()
    weightedData.map { w =>
                        val charArr = w._2.flatMap(d => d.toLowerCase.toList)
                        val uniqueLen = charArr.distinct.size
                        val uniqueCharArr = charArr.distinct
                        var featureInds = new Array[Int](uniqueLen)
                        var featureVals = new Array[Double](uniqueLen)
                        var i = 0
                        while(i < uniqueLen) {
                            val numOfOcc = charArr.count(_ == uniqueCharArr(i))
                            if(uniqueCharArr(i) == 'a' || uniqueCharArr(i) == 'A') {
                                featureInds(i) = 0
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'b' || uniqueCharArr(i) == 'B') {
                                featureInds(i) = 1
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'c' || uniqueCharArr(i) == 'C') {
                                featureInds(i) = 2
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'd' || uniqueCharArr(i) == 'D') {
                                featureInds(i) = 3
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'e' || uniqueCharArr(i) == 'E') {
                                featureInds(i) = 4
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'f' || uniqueCharArr(i) == 'F') {
                                featureInds(i) = 5
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'g' || uniqueCharArr(i) == 'G') {
                                featureInds(i) = 6
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'h' || uniqueCharArr(i) == 'H') {
                                featureInds(i) = 7
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'i' || uniqueCharArr(i) == 'I') {
                                featureInds(i) = 8
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'j' || uniqueCharArr(i) == 'J') {
                                featureInds(i) = 9
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'k' || uniqueCharArr(i) == 'K') {
                                featureInds(i) = 10
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'l' || uniqueCharArr(i) == 'L') {
                                featureInds(i) = 11
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'm' || uniqueCharArr(i) == 'M') {
                                featureInds(i) = 12
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'n' || uniqueCharArr(i) == 'N') {
                                featureInds(i) = 13
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'o' || uniqueCharArr(i) == 'O') {
                                featureInds(i) = 14
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'p' || uniqueCharArr(i) == 'P') {
                                featureInds(i) = 15
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'q' || uniqueCharArr(i) == 'Q') {
                                featureInds(i) = 16
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'r' || uniqueCharArr(i) == 'R') {
                                featureInds(i) = 17
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 's' || uniqueCharArr(i) == 'S') {
                                featureInds(i) = 18
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 't' || uniqueCharArr(i) == 'T') {
                                featureInds(i) = 19
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'u' || uniqueCharArr(i) == 'U') {
                                featureInds(i) = 20
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'v' || uniqueCharArr(i) == 'V') {
                                featureInds(i) = 21
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'w' || uniqueCharArr(i) == 'W') {
                                featureInds(i) = 22
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'x' || uniqueCharArr(i) == 'X') {
                                featureInds(i) = 23
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'y' || uniqueCharArr(i) == 'Y') {
                                featureInds(i) = 24
                                featureVals(i) = numOfOcc
                            } else if (uniqueCharArr(i) == 'z' || uniqueCharArr(i) == 'Z') {
                                featureInds(i) = 25
                                featureVals(i) = numOfOcc
                            }

                            i = i + 1
                        }
                        // ignore the weights for now...
                        LabeledPoint(revDict(w._1(0)), Vectors.sparse(26,featureInds,featureVals))
                }

  }


  /**
   * Build an index of every word (not including the definitions, only words)
   * @param edges A collection of edges between a word and another word
   * @return An RDD of unique index and word
   * @see crosswords.spark.Dictionary#build
   */
  def createDictionary(edges: RDD[(String, String, Float)]): RDD[(Long, String)] = {
    val flatten = edges.flatMap(t => Array(t._1))
    Dictionary.build(flatten)
  }


  /**
   * Build each edge of the graph.
   * @param extracted An RDD where each element is a word/sentence, its definition and the category weight
   * @return An RDD where each element represent an edge between the two words and the category weight
   */
  def buildEdges(extracted: RDD[(Seq[String], Seq[String], Float)]): RDD[(String, String, Float)] = {
    // Flattens the content of the tuples: foreach tuple, foreach word w1, foreach word w2, we yield (w1, w2, weight)
    extracted.flatMap(t => t._1.flatMap(w1 => t._2.map(w2 => (w1, w2, t._3))))
  }

  def indexOfLargest(array: Array[Double]): Int = {
    val result = array.foldLeft(-1,Double.MinValue,0) {
        case ((maxIndex, maxValue, currentIndex), currentValue) =>
            if(currentValue > maxValue) (currentIndex,currentValue,currentIndex+1)
            else (maxIndex,maxValue,currentIndex+1)
        }
    result._1
 }
}
