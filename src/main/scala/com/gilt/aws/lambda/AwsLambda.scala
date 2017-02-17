package com.gilt.aws.lambda

import com.amazonaws.regions.RegionUtils
import com.amazonaws.{AmazonServiceException, AmazonClientException}
import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.model._
import sbt._

import scala.util.{Try, Failure, Success}

private[lambda] object AwsLambda {

  def deployLambda(region: Region,
                   jar: File,
                   functionName: LambdaName,
                   handlerName: HandlerName,
                   roleName: RoleARN,
                   s3BucketId: S3BucketId,
                   s3Key: S3Key,
                   timeout:  Option[Timeout],
                   memory: Option[Memory]
                  ): Try[Either[CreateFunctionResult, UpdateFunctionCodeResult]] = {
    try {
      val client = new AWSLambdaClient(AwsCredentials.provider)
      client.setRegion(RegionUtils.getRegion(region.value))

      val isNew = try {
        val getRequest = new GetFunctionRequest
        getRequest.setFunctionName(functionName.value)
        client.getFunction(getRequest)
        false
      }
      catch {
        case _ : ResourceNotFoundException => true
      }

      if(isNew) {
        val request = {
          val r = new CreateFunctionRequest()
          r.setFunctionName(functionName.value)
          r.setHandler(handlerName.value)
          r.setRole(roleName.value)
          r.setRuntime(com.amazonaws.services.lambda.model.Runtime.Java8)
          if(timeout.isDefined) r.setTimeout(timeout.get.value)
          if(memory.isDefined)  r.setMemorySize(memory.get.value)

          val functionCode = {
            val c = new FunctionCode
            c.setS3Bucket(s3BucketId.value)
            c.setS3Key(jar.getName)
            c
          }

          r.setCode(functionCode)

          r
        } 

        val createResult = client.createFunction(request)

        println(s"Created Lambda: ${createResult.getFunctionArn}")
        Success(Left(createResult))

      } else {
        val request = {
          val r = new UpdateFunctionCodeRequest()
          r.setFunctionName(functionName.value)
          r.setS3Bucket(s3BucketId.value)
          r.setS3Key(s3Key.value)

          r
        }

        val updateResult = client.updateFunctionCode(request)

        println(s"Updated lambda ${updateResult.getFunctionArn}")
        Success(Right(updateResult))
      }
    }
    catch {
      case ex @ (_ : AmazonClientException |
                 _ : AmazonServiceException) =>
        Failure(ex)
    }
  }

}
