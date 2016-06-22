import sbt.Keys._
import sbt._
import Keys._

import com.twitter.scrooge.ScroogeSBT.autoImport._

object OyTimeline extends Build {
  val branch = Process("git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil).!!.trim
  val suffix = ""

  val oyversion = "1.0.0"

  val finagleVersion = "6.35.0"
  val scroogeVersion = "4.7.0"
  val libthriftVersion = "0.5.0-1"

  val thriftLibs = Seq(
    "org.apache.thrift" % "libthrift" % libthriftVersion intransitive(),
    "org.slf4j" % "slf4j-api" % "1.7.7" % "provided"
  )
  val scroogeLibs = thriftLibs ++ Seq(
    "com.twitter" %% "scrooge-core" % scroogeVersion)

  val finagleLibs = Seq(
    "com.twitter" %% "finagle-core" % finagleVersion,
    "com.twitter" %% "finagle-stats" % finagleVersion,
    "com.twitter" %% "finagle-http" % finagleVersion,
    "com.twitter" %% "finagle-thriftmux" % finagleVersion,
    "com.twitter" %% "twitter-server" % "1.20.0"
  )

  val defaultScroogeSettings = Seq(
    scroogeThriftOutputFolder in Compile <<= baseDirectory { base => base / "src" / "generated" / "scala" },
    unmanagedSourceDirectories in Compile += baseDirectory.value / "src" / "generated" / "scala",
    scroogeThriftIncludeFolders in Compile += file("shared") / "src" / "main"/ "thrift"
  )

  val sharedSettings = Seq(
    version := oyversion,
    organization := "com.fullcontact",
    scalaVersion := "2.11.8",
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test",
      "junit" % "junit" % "4.10" % "test",
      "org.mockito" % "mockito-all" % "1.9.5" % "test"
    ),
    resolvers += "twitter-repo" at "https://maven.twttr.com"
  )

  lazy val oy = Project(
    id = "oy-timeline",
    base = file("."),
    settings = Defaults.coreDefaultSettings ++ sharedSettings
  ) aggregate(
    shared, storage, timeline, ids
  )

  lazy val shared = Project(
    id = "shared",
    base = file("shared"),
    settings = Defaults.coreDefaultSettings ++ sharedSettings ++ defaultScroogeSettings
  ).settings(
    name := "shared",
    libraryDependencies ++= finagleLibs ++ scroogeLibs
  ).dependsOn()

  lazy val storage = Project(
    id = "storage",
    base = file("storage"),
    settings = Defaults.coreDefaultSettings ++ sharedSettings ++ defaultScroogeSettings
  ).settings(
    name := "storage",
    libraryDependencies ++=
      finagleLibs ++
        scroogeLibs ++
        Seq(
          "com.twitter" %% "finagle-redis" % finagleVersion,
          "com.twitter" %% "bijection-core" % "0.9.2",
          "com.twitter" %% "bijection-scrooge" % "0.9.2"
        )
  ).dependsOn(shared)

  lazy val timeline = Project(
    id = "timeline",
    base = file("timeline"),
    settings = Defaults.coreDefaultSettings ++ sharedSettings ++ defaultScroogeSettings
  ).settings(
    name := "timeline",
    libraryDependencies ++=
      finagleLibs ++
        scroogeLibs ++
        Seq(
          "com.twitter" %% "finagle-redis" % finagleVersion,
          "com.twitter" %% "bijection-core" % "0.9.2",
          "com.twitter" %% "bijection-scrooge" % "0.9.2"
        )
  ).dependsOn(shared, ids, storage)

  lazy val ids = Project(
    id = "ids",
    base = file("ids"),
    settings = Defaults.coreDefaultSettings ++ sharedSettings ++ defaultScroogeSettings
  ).settings(
    name := "ids",
    libraryDependencies ++=
      finagleLibs ++
        scroogeLibs ++
        Seq(
          "com.twitter" %% "finagle-redis" % finagleVersion,
          "com.twitter" %% "bijection-core" % "0.9.2",
          "com.twitter" %% "bijection-scrooge" % "0.9.2"
        )
  ).dependsOn(shared)
}
