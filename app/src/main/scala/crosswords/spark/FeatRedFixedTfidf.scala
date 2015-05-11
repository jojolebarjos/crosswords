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
object FeatRedFixedTfidf {
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
//           .set("spark.executor.memory", "20g")
//           .set("spark.driver.memory","20g")
//           .set("spark.cores.max","40")
//           .set("spark.storage.memoryFraction", "0.2")
//           .set("spark.task.maxFailures","4")
//           .set("spark.yarn.executor.memoryOverhead", "80")
//           .set("spark.driver.maxResultSize","20g");
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
    val weightedDataTemp = weightCategories(cleanData)

    // reduce the number of samples
    println("randomly sampling to reduce the number of samples...")
    val temp = weightedDataTemp.randomSplit(Array(0.001,0.99), seed = 11L);
    val weightedData = temp(0);

    println("Getting the tf-idf values for each word...");
    val toTfIdf = weightedData.map{ l => l._2 }
    val hashingTF = new HashingTF()
    val tf: RDD[Vector] = hashingTF.transform(toTfIdf)
    tf.cache()
    val idf = new IDF().fit(tf)
    val tfidf: RDD[Vector] = idf.transform(tf)

    // Due to the memory limit we fix the total number of features to 1000, otherwise we would use some statistics such as mean
    // stddev and histogra to decide on TOPTFIDF threshold
    val topTfidfs = tfidf.map(e => e.toArray.filter(b => b > 0)).flatMap(x => x).takeOrdered(TOPTFIDF)(Ordering[(Double)].reverse)
    val minThres = topTfidfs.last

    println("Extracting the features, top #" + TOPTFIDF + "...")
    val reducedData = weightedData.zip(tfidf).map { e =>
                    // find the indices of the top tfidf words
                    var arr = e._2.toArray.filter(e => e > 0)
                    var clues = e._1._2
                    //println("the tfidf array:" + arr.mkString(","))
                    var j = 0;
                    val len = arr.size
                    var newWords = Seq.empty[String]
                    var fin = 0;
                    while(j < len && fin == 0) {
                        val ind = indexOfLargest(arr)
                        if(arr(ind) < minThres) {
                            fin = 1
                        }
                        else {
                            arr(ind) = -100
                            newWords = newWords :+ clues(ind)
                        }
                        j = j + 1
                    }
                    (e._1._1,newWords,e._1._3)
          }

    println("Saving the reduced data with extracted faetures to the disk...")
    reducedData.saveAsTextFile("reducedDataFixTop10000-From0.001Data");


    // comment-in/out the code below to directly load the reducedData (if already saved)
    /*
    println("Loading the reduced data directly from a file...");
    val reducedDataStr = context.textFile("./reducedDataFixTop10000-From0.001Data")

    // convert reducedData, which is and RDD[String], to its original format
    val reducedData = reducedDataStr.map { l =>
                                        val wordDefnWeight = l.map { c =>
                                                if(c == '(' || c == ')')
                                                        '|'
                                                else
                                                        c
                                        }.split('|')
                                        val word = Seq(wordDefnWeight(2))
                                        val defns =  wordDefnWeight(4)
                                        val defnsArr = defns.split(", ").toSeq
                                        val weight = wordDefnWeight.last.substring(1).toFloat
                                        (word,defnsArr,weight)
                                    }

    */
    println("Building the feature vectors...")

    // Rest of the flow is similar to the previous similarity matrix
    println("Building the edges...")
    val edges = buildEdges(reducedData)

    println("Building the feature ids...")
    val featureIds = createFeatureIds(edges)

    println("Building the label ids...")
    val labelIds = createLabelIds(edges)

    println("Building the indexed edges...")
    val indexed = toIndex(edges, featureIds, labelIds)

    println("Building the combined resulting adjancency matrix...")
    val result = combine(indexed)

    println("Building the predata...")
    val predata = result.map{l =>
                                (l._1,(l._2.toInt.toString + "-" + "%.3f".format(l._3),l._3))
                        }.reduceByKey( (a,b) => (a._1 + " " + b._1, a._2 + b._2 ) )
   
    val indMax = featureIds.count.toInt
    println("Building the final data set for feeding to machine learning...")
    val data = predata.map{ l => // processing the line to create a feature vector from them
                            val l2 = l._2._1.split(" ")
                            val size = l2.size
                            var indices = Array[Int]();
                            var values = Array[Double]();
                            var i = 0;
                            while(i < size) {
                                val l3 = l2(i).split("-");
                                indices = indices :+ l3(0).toInt
                                val feature = l3(1).toDouble;
                                values = values :+ feature;
                                i = i + 1;
                            }
                            //(l._1,maxval,indices,values)
                            LabeledPoint(l._1,Vectors.sparse(indMax, indices, values ))
                            //Vectors.sparse(maxval.toInt, indices, values);
                            //LabeledPoint(maxval,Vectors.sparse(12,Array(3,6,9),Array(0.5,3.6,7.2)))
                        }

    //predata.saveAsTextFile("predata");
    //data.saveAsTextFile("data");
    //weightedData.saveAsTextFile("weightedData")
    //edges.saveAsTextFile("edges")
    //dict.saveAsTextFile("dict")
    //indexed.saveAsTextFile("indexed")
    //result.saveAsTextFile("result")

    // do five runs of training/testing with different random splits with the reduced data

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

    val defs = context.textFile(inputDirectory + File.separator + "defs/part-*").map(parseCSV)
    val examples = context.textFile(inputDirectory + File.separator + "examples/part-*").map(parseCSV)
    val equiv = context.textFile(inputDirectory + File.separator + "equiv/part-*").map(parseCSV)
    val asso = context.textFile(inputDirectory + File.separator + "asso/part-*").map(parseCSV)
    Seq(clues, defs, examples, equiv, asso)

  }

  /**
   * Weight each category and group all the collections together.
   * @param l A sequence of RDD containing the cleaned data from the clues, definitions, examples, equivalents and associated
   * @return A union of all the collections after adding the category weights.
   */
  def weightCategories(l: Seq[RDD[(Seq[String], Seq[String])]]): RDD[(Seq[String], Seq[String], Float)] = {
    // first only use the crosswords data -- utku
    l(0).map(t => (t._1, t._2, 1.toFloat))  ++
      l(1).map(t => (t._1, t._2, DEFS_WEIGHT)) ++
      l(2).map(t => (t._1, t._2, EXAMPLES_WEIGHT)) ++
      l(3).map(t => (t._1, t._2, EQUIV_WEIGHT)) ++
      l(4).map(t => (t._1, t._2, ASSO_WEIGHT))
  }

  /**
   * Build an index of every word in the feature sets
   * @param edges A collection of edges between a word and its features
   * @return An RDD of unique index and word
   * @see crosswords.spark.Dictionary#build
   */
  def createFeatureIds(edges: RDD[(String, String, Float)]): RDD[(Long, String)] = {
    val flatten = edges.flatMap(t => Array(t._2))
    Dictionary.build(flatten)
  }

  /**
   * Build an index of every word in the labels
   * @param edges A collection of edges between a word and its features
   * @return An RDD of unique index and word
   * @see crosswords.spark.Dictionary#build
   */
  def createLabelIds(edges: RDD[(String, String, Float)]): RDD[(Long, String)] = {
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

  /**
   * Transform a collection of string-indexed edges to a collection of indexes-indexed edges.
   * @param edges A collection of edges with their weights
   * @param dictionary A collection of all the vocabulary with a unique index
   * @return A collection of edges indexed by indexes
   */
  def toIndex(edges: RDD[(String, String, Float)], featureIds: RDD[(Long, String)], labelIds: RDD[(Long, String)]): RDD[((Long, Long), Float)] = {
    val featureIdsRev = Dictionary.swap(featureIds).collectAsMap()
    val labelIdsRev = Dictionary.swap(labelIds).collectAsMap()

    edges.map(t => ((labelIdsRev(t._1), featureIdsRev(t._2)), t._3))
  }

  /**
   * Compute and normalize the similarity between words. The similarity is between 0 (inclusive) and 1 (inclusive).
   * @param edges A collection of edges with their category weights
   * @return A collection of edges with the similarity between the two words
   */
  def combine(edges: RDD[((Long, Long), Float)]): RDD[(Long, Long, Float)] = {
    val combined = edges.reduceByKey((c1, c2) => c1 + c2)
    // Group by row, then normalize each line by dividing by its maximum value
    val rowIndexed = combined.map(t => (t._1._1, (t._1._2, t._2))).groupByKey()
    rowIndexed.flatMap { row =>
      val maxValue = row._2.maxBy(t => t._2)._2
      row._2.map(t => (row._1, t._1, t._2 / maxValue))
    }
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
 						
