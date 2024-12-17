import sbt.Keys.javacOptions

lazy val root = (project in file(".")).
  settings(
    name := "getting-started-scala",
    version := "1.0",
    scalaVersion := "2.12.20",
    mainClass := Some("com.amazonaws.services.msf.BasicStreamingJob"),
    javacOptions ++= Seq("-source", "11", "-target", "11")
  )

val jarName = "ScalaGettingStarted-flink_1.20.jar"
val flinkVersion = "1.20.0"
val msfRuntimeVersion = "1.2.0"
val connectorVersion = "4.3.0-1.19"
val log4jVersion = "2.17.2"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-kinesisanalytics-runtime" % msfRuntimeVersion,
  "org.apache.flink" % "flink-connector-kinesis" % connectorVersion,
  "org.apache.flink" % "flink-streaming-java" % flinkVersion % "provided",
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
  "org.apache.flink" % "flink-connector-base" % flinkVersion % "provided",
  "org.apache.flink" % "flink-clients" % flinkVersion % "provided",
  // Logging
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion % "provided",
  "org.apache.logging.log4j" % "log4j-api" % log4jVersion % "provided",
  "org.apache.logging.log4j" % "log4j-core" % log4jVersion % "provided",
)

artifactName := { (_: ScalaVersion, _: ModuleID, _: Artifact) => jarName }

assembly / assemblyJarName := jarName
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}