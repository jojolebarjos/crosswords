version := "1.0"

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.typesafe.play" %% "play-json" % "2.3.8"

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.12"

// To run on the cluster, create the jar with "sbt cluster compile assembly"
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "1.2.1",
  "org.apache.spark" %% "spark-mllib" % "1.2.1",
  "org.apache.spark" %% "spark-graphx" % "1.2.1"
)

libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.12"

libraryDependencies += "org.wikimodel" % "org.wikimodel.wem" % "2.0.7"

libraryDependencies += "org.codehaus.woodstox" % "woodstox-core-asl" % "4.4.1"

libraryDependencies += "edu.mit" % "jwi" % "2.2.3"
