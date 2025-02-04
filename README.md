# S3 Parquet output plugin for Embulk

[Embulk](https://github.com/embulk/embulk/) output plugin to dump records as [Apache Parquet](https://parquet.apache.org/) files on S3.

## Overview

* **Plugin type**: output
* **Load all or nothing**: no
* **Resume supported**: no
* **Cleanup supported**: yes

## Configuration

- **bucket**: s3 bucket name (string, required)
- **path_prefix**: prefix of target keys (string, optional)
- **sequence_format**: format of the sequence number of the output files (string, default: `"%03d.%02d."`)
  - **sequence_format** formats task index and sequence number in a task. 
- **file_ext**: path suffix of the output files (string, default: `"parquet"`)
- **compression_codec**: compression codec for parquet file (`"uncompressed"`,`"snappy"`,`"gzip"`,`"lzo"`,`"brotli"`,`"lz4"` or `"zstd"`, default: `"uncompressed"`)
- **default_timestamp_format**: default timestamp format (string, default: `"%Y-%m-%d %H:%M:%S.%6N %z"`)
- **default_timezone**: default timezone (string, default: `"UTC"`)
- **column_options**: a map whose keys are name of columns, and values are configuration with following parameters (optional)
  - **timezone**: timezone if type of this column is timestamp. If not set, **default_timezone** is used. (string, optional)
  - **format**: timestamp format if type of this column is timestamp. If not set, **default_timestamp_format**: is used. (string, optional)
- **canned_acl**: grants one of [canned ACLs](https://docs.aws.amazon.com/AmazonS3/latest/dev/acl-overview.html#CannedACL) for created objects (string, default: `private`)
- **block_size**: The block size is the size of a row group being buffered in memory. This limits the memory usage when writing. Larger values will improve the I/O when reading but consume more memory when writing. (int, default: `134217728` (128MB))
- **page_size**: The page size is for compression. When reading, each page can be decompressed independently. A block is composed of pages. The page is the smallest unit that must be read fully to access a single record. If this value is too small, the compression will deteriorate. (int, default: `1048576` (1MB))
- **max_padding_size**: The max size (bytes) to write as padding and the min size of a row group (int, default: `8388608` (8MB))
- **enable_dictionary_encoding**: The boolean value is to enable/disable dictionary encoding. (boolean, default: `true`)
- **auth_method**: name of mechanism to authenticate requests (`"basic"`, `"env"`, `"instance"`, `"profile"`, `"properties"`, `"anonymous"`, or `"session"`, default: `"default"`)
  - `"basic"`: uses **access_key_id** and **secret_access_key** to authenticate.
  - `"env"`: uses `AWS_ACCESS_KEY_ID` (or `AWS_ACCESS_KEY`) and `AWS_SECRET_KEY` (or `AWS_SECRET_ACCESS_KEY`) environment variables.
  - `"instance"`: uses EC2 instance profile or attached ECS task role.
  - `"profile"`: uses credentials written in a file. Format of the file is as following, where `[...]` is a name of profile.
    ```
    [default]
    aws_access_key_id=YOUR_ACCESS_KEY_ID
    aws_secret_access_key=YOUR_SECRET_ACCESS_KEY

    [profile2]
    ...
    ```
  - `"properties"`: uses aws.accessKeyId and aws.secretKey Java system properties.
  - `"anonymous"`: uses anonymous access. This auth method can access only public files.
  - `"session"`: uses temporary-generated **access_key_id**, **secret_access_key** and **session_token**.
  - `"assume_role"`: uses temporary-generated credentials by assuming **role_arn** role.
  - `"default"`: uses AWS SDK's default strategy to look up available credentials from runtime environment. This method behaves like the combination of the following methods.
    1. `"env"`
    1. `"properties"`
    1. `"profile"`
    1. `"instance"`
- **profile_file**: path to a profiles file. this is optionally used when **auth_method** is `"profile"`. (string, default: given by `AWS_CREDENTIAL_PROFILES_FILE` environment variable, or ~/.aws/credentials).
- **profile_name**: name of a profile. this is optionally used when **auth_method** is `"profile"`. (string, default: `"default"`)
- **access_key_id**: aws access key id. this is required when **auth_method** is `"basic"` or `"session"`. (string, optional)
- **secret_access_key**: aws secret access key. this is required when **auth_method** is `"basic"` or `"session"`. (string, optional)
- **session_token**: aws session token. this is required when **auth_method** is `"session"`. (string, optional)
- **role_arn**: arn of the role to assume. this is required for **auth_method** is `"assume_role"`. (string, optional)
- **role_session_name**: an identifier for the assumed role session. this is required when **auth_method** is `"assume_role"`. (string, optional)  
- **role_external_id**: a unique identifier that is used by third parties when assuming roles in their customers' accounts. this is optionally used for **auth_method**: `"assume_role"`. (string, optional)
- **role_session_duration_seconds**: duration, in seconds, of the role session. this is optionally used for **auth_method**: `"assume_role"`. (int, optional) 
- **scope_down_policy**: an iam policy in json format. this is optionally used for **auth_method**: `"assume_role"`. (string, optional)
- **catalog**: Register a table if this option is specified (optional)
  - **catalog_id**: glue data catalog id if you use a catalog different from account/region default catalog. (string, optional)
  - **database**: The name of the database (string, required)
  - **table**: The name of the table (string, required)
  - **column_options**: a key-value pairs where key is a column name and value is options for the column. (string to options map, default: `{}`)
    - **type**: type of a column when this plugin creates new tables (e.g. `STRING`, `BIGINT`) (string, default: depends on input column type. `BIGINT` if input column type is `long`, `BOOLEAN` if boolean, `DOUBLE` if `double`, `STRING` if `string`, `STRING` if `timestamp`, `STRING` if `json`)
  - **operation_if_exists**: operation if the table already exist. Available operations are `"delete"` and `"skip"` (string, default: `"delete"`)
- **endpoint**: The AWS Service endpoint (string, optional)
- **region**: The AWS region (string, optional)
- **http_proxy**: Indicate whether using when accessing AWS via http proxy. (optional)
  - **host** proxy host (string, required)
  - **port** proxy port (int, optional)
  - **protocol** proxy protocol (string, default: `"https"`)
  - **user** proxy user (string, optional)
  - **password** proxy password (string, optional)
- **buffer_dir**: buffer directory for parquet files to be uploaded on S3 (string, default: Create a Temporary Directory)


## Example

```yaml
out:
  type: s3_parquet
  bucket: my-bucket
  path_prefix: path/to/my-obj.
  file_ext: snappy.parquet
  compression_codec: snappy
  default_timezone: Asia/Tokyo
  canned_acl: bucket-owner-full-control
```

## Note

* The current implementation does not support [LogicalTypes](https://github.com/apache/parquet-format/blob/2b38663/LogicalTypes.md). I will implement them later as **column_options**. So, currently **timestamp** type and **json** type are stored as UTF-8 String. Please be careful.  

## Development

### Run example:

```shell
$ ./gradlew classpath
$ embulk run example/config.yml -Ilib
```

### Run test:

```shell
$ ./gradlew test
```

### Build

```
$ ./gradlew gem  # -t to watch change of files and rebuild continuously
```

### Release gem:
Fix [build.gradle](./build.gradle), then


```shell
$ ./gradlew gemPush

```

## ChangeLog

[CHANGELOG.md](./CHANGELOG.md)
