package org.embulk.output.s3_parquet


import java.util.{Optional, Map => JMap}

import com.amazonaws.services.glue.model.{Column, CreateTableRequest, DeleteTableRequest, GetTableRequest, SerDeInfo, StorageDescriptor, TableInput}
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.embulk.config.{Config, ConfigDefault, ConfigException}
import org.embulk.output.s3_parquet.aws.Aws
import org.embulk.output.s3_parquet.CatalogRegistrator.ColumnOptions
import org.embulk.spi.Schema
import org.embulk.spi.`type`.{BooleanType, DoubleType, JsonType, LongType, StringType, TimestampType, Type}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters._
import scala.util.Try


object CatalogRegistrator
{
    trait Task
        extends org.embulk.config.Task
    {
        @Config("catalog_id")
        @ConfigDefault("null")
        def getCatalogId: Optional[String]

        @Config("database")
        def getDatabase: String

        @Config("table")
        def getTable: String

        @Config("column_options")
        @ConfigDefault("{}")
        def getColumnOptions: JMap[String, ColumnOptions]

        @Config("operation_if_exists")
        @ConfigDefault("\"delete\"")
        def getOperationIfExists: String
    }

    trait ColumnOptions
    {
        @Config("type")
        def getType: String
    }

    def apply(aws: Aws,
              task: Task,
              schema: Schema,
              location: String,
              compressionCodec: CompressionCodecName,
              loggerOption: Option[Logger] = None): CatalogRegistrator =
    {
        new CatalogRegistrator(aws, task, schema, location, compressionCodec, loggerOption)
    }
}

class CatalogRegistrator(aws: Aws,
                         task: CatalogRegistrator.Task,
                         schema: Schema,
                         location: String,
                         compressionCodec: CompressionCodecName,
                         loggerOption: Option[Logger] = None)
{
    val logger: Logger = loggerOption.getOrElse(LoggerFactory.getLogger(classOf[CatalogRegistrator]))

    def run(): Unit =
    {
        if (doesTableExists()) {
            task.getOperationIfExists match {
                case "skip" =>
                    logger.info(s"Skip to register the table: ${task.getDatabase}.${task.getTable}")
                    return

                case "delete" =>
                    logger.info(s"Delete the table: ${task.getDatabase}.${task.getTable}")
                    deleteTable()

                case unknown =>
                    throw new ConfigException(s"Unsupported operation: $unknown")
            }
        }
        registerNewParquetTable()
        showNewTableInfo()
    }

    def showNewTableInfo(): Unit =
    {
        val req = new GetTableRequest()
        task.getCatalogId.ifPresent(cid => req.setCatalogId(cid))
        req.setDatabaseName(task.getDatabase)
        req.setName(task.getTable)

        val t = aws.withGlue(_.getTable(req)).getTable
        logger.info(s"Created a table: ${t.toString}")
    }

    def doesTableExists(): Boolean =
    {
        val req = new GetTableRequest()
        task.getCatalogId.ifPresent(cid => req.setCatalogId(cid))
        req.setDatabaseName(task.getDatabase)
        req.setName(task.getTable)

        Try(aws.withGlue(_.getTable(req))).isSuccess
    }

    def deleteTable(): Unit =
    {
        val req = new DeleteTableRequest()
        task.getCatalogId.ifPresent(cid => req.setCatalogId(cid))
        req.setDatabaseName(task.getDatabase)
        req.setName(task.getTable)
        aws.withGlue(_.deleteTable(req))
    }

    def registerNewParquetTable(): Unit =
    {
        logger.info(s"Create a new table: ${task.getDatabase}.${task.getTable}")
        val req = new CreateTableRequest()
        task.getCatalogId.ifPresent(cid => req.setCatalogId(cid))
        req.setDatabaseName(task.getDatabase)
        req.setTableInput(new TableInput()
                              .withName(task.getTable)
                              .withDescription("Created by embulk-output-s3_parquet")
                              .withTableType("EXTERNAL_TABLE")
                              .withParameters(Map("EXTERNAL" -> "TRUE",
                                                  "classification" -> "parquet",
                                                  "parquet.compression" -> compressionCodec.name()).asJava)
                              .withStorageDescriptor(new StorageDescriptor()
                                                         .withColumns(getGlueSchema: _*)
                                                         .withLocation(location)
                                                         .withCompressed(isCompressed)
                                                         .withInputFormat("org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat")
                                                         .withOutputFormat("org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat")
                                                         .withSerdeInfo(new SerDeInfo()
                                                                            .withSerializationLibrary("org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe")
                                                                            .withParameters(Map("serialization.format" -> "1").asJava)
                                                                        )
                                                     )
                          )
        aws.withGlue(_.createTable(req))
    }

    private def getGlueSchema: Seq[Column] =
    {
        val columnOptions: Map[String, ColumnOptions] = task.getColumnOptions.asScala.toMap
        schema.getColumns.asScala.toSeq.map { c =>
            val cType: String =
                if (columnOptions.contains(c.getName)) columnOptions(c.getName).getType
                else convertEmbulkType2GlueType(c.getType)
            new Column()
                .withName(c.getName)
                .withType(cType)
        }
    }

    private def convertEmbulkType2GlueType(t: Type): String =
    {
        t match {
            case _: BooleanType   => "boolean"
            case _: LongType      => "bigint"
            case _: DoubleType    => "double"
            case _: StringType    => "string"
            case _: TimestampType => "string"
            case _: JsonType      => "string"
            case unknown          => throw new ConfigException(s"Unsupported embulk type: ${unknown.getName}")
        }
    }

    private def isCompressed: Boolean =
    {
        !compressionCodec.equals(CompressionCodecName.UNCOMPRESSED)
    }

}
