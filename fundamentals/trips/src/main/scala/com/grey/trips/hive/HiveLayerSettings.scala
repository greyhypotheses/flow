package com.grey.trips.hive

import java.nio.file.Path
import java.sql.Date

import com.grey.trips.environment.DataDirectories
import com.grey.trips.functions.DataReset
import com.grey.libraries.hive.{ClearUp, HiveBaseCaseClass, HiveLayer, HiveLayerCaseClass}
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.collection.parallel.mutable.ParArray
import scala.util.Try
import scala.util.control.Exception

class HiveLayerSettings(spark: SparkSession, hiveBaseProperties: HiveBaseCaseClass#HBCC) {

  private val dataReset = new DataReset()
  private val dataDirectories = new DataDirectories()
  private val clearUp = new ClearUp(spark, hiveBaseProperties)
  private val hiveLayer = new HiveLayer(spark, hiveBaseProperties)
  private val hiveLayerProperties = new HiveLayerProperties()

  def hiveLayerSettings(date: Date, src: String, dst: String, partition: String): Try[Unit] = {


    // Hive layer properties w.r.t. the date in question
    val HLP: HiveLayerCaseClass#HLCC = hiveLayerProperties.hiveLayerProperties(date = date, partition = partition)


    // Clear the Hive table partition that will be updated
    val clear: Try[Unit] = Exception.allCatch.withTry(
      clearUp.clearUp(hiveLayerProperties = HLP)
    )


    // Move the prospective/new table partition files to the Hive hub.  Contents of the
    // 'dst' directory are cleared beforehand
    val move: ParArray[Try[Path]] = if (clear.isSuccess) {
      dataReset.dataReset(source = src, destination = dst)
    } else {
      sys.error(clear.failed.get.getMessage)
    }


    // Register
    val layer: Try[DataFrame] = if (move.last.isSuccess) {
      hiveLayer.hiveLayer(hiveLayerProperties = HLP)
    } else {
      sys.error(move.last.failed.get.getMessage)
    }


    // Finally
    if (layer.isSuccess) {
      dataDirectories.localDirectoryDelete(directoryName = src)
    } else {
      List(src, dst).par.foreach(dataDirectories.localDirectoryDelete)
      sys.error(layer.failed.get.getMessage)
    }


  }


}