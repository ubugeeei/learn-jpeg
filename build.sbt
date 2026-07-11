ThisBuild / scalaVersion := "3.7.1"
ThisBuild / organization := "io.github.ubugeeei"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = project.in(file("."))
  .settings(
    name := "learn-jpeg",
    libraryDependencies += "org.scalameta" %% "munit" % "1.1.1" % Test,
    Test / parallelExecution := true,
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Werror")
  )
