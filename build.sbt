name := "reactify"
organization in ThisBuild := "com.outr"
version in ThisBuild := "1.7.0-SNAPSHOT"
scalaVersion in ThisBuild := "2.12.2"
crossScalaVersions in ThisBuild := List("2.12.2", "2.11.11")

lazy val root = project.in(file("."))
  .aggregate(js, jvm)
  .settings(
    publish := {},
    publishLocal := {}
  )

lazy val reactify = crossProject.in(file("."))
  .settings(
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.3" % "test"
  )
lazy val js = reactify.js
lazy val jvm = reactify.jvm