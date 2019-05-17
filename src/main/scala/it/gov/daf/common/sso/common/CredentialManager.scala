/*
 * Copyright 2017 - 2018 TEAM PER LA TRASFORMAZIONE DIGITALE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.gov.daf.common.sso.common

import java.util

import it.gov.daf.common.authentication.Authentication
import it.gov.daf.common.utils._
import org.apache.commons.codec.binary.Base64
//import org.apache.commons.net.util.Base64
import play.api.Logger
import play.api.mvc.{Request, RequestHeader}
import scala.util.Try

@SuppressWarnings(
  Array(
  "org.wartremover.warts.Throw",
  "org.wartremover.warts.ToString",
  "org.wartremover.warts.TraversableOps",
  "org.wartremover.warts.OptionPartial",
  "org.wartremover.warts.AsInstanceOf"
  )
)
object CredentialManager {

  def readBaCredentials( requestHeader:RequestHeader):Option[Credentials]= {

    val authHeader = requestHeader.headers.get("authorization").get.split(" ")
    val authType = authHeader(0)
    val authCrendentials = authHeader(1)


    if( authType.equalsIgnoreCase("basic") ){

      val pwd:String = new String(Base64.decodeBase64(authCrendentials.getBytes)).split(":")(1)
      val user:String= Authentication.getProfiles(requestHeader).head.getId
      val ldapGroups = Authentication.getProfiles(requestHeader).head.getAttribute("memberOf") match {
        case coll: util.Collection[_] => coll.asInstanceOf[util.Collection[String]].toArray()
        case s: String => Array(s)
        case _ => throw new Exception("User with no associated profiles")
      }

      val groups: Array[String] = ldapGroups.map( _.toString().split(",")(0).split("=")(1) )

      Some( Credentials(user, pwd, groups) )
    }else
      None

  }


  def readBearerCredentials(requestHeader: RequestHeader):Option[Profile]= {

    val authHeader = requestHeader.headers.get("authorization").get.split(" ")
    val authType = authHeader(0)
    val token = authHeader(1)

    if( authType.equalsIgnoreCase("bearer") ) {

      //Logger.logger.debug(s"claims:${Authentication.getClaims(requestHeader)}")

      val claims = Authentication.getClaims(requestHeader).get
      val ldapGroups = claims.get("memberOf").asInstanceOf[Option[Any]].get.asInstanceOf[net.minidev.json.JSONArray].toArray
      val groups: Array[String] = ldapGroups.map(_.toString.split(",")(0).split("=")(1))
      val user: String = claims("sub").toString

      //Logger.logger.info(s"JWT user: $user")
      //Logger.logger.info(s"belonging to groups: ${groups.toList}" )

      Some( Profile(user, token, groups) )
    }else
      None

  }

  def readCredentialFromRequest( requestHeader: RequestHeader ):UserInfo = {

    readBearerCredentials(requestHeader) match {
      case Some(profile) => profile
      case None => readBaCredentials(requestHeader) match{
        case Some(credentials) => credentials
        case None => throw new Exception("Authorization header not found")
      }
    }

  }

  def tryToReadCredentialFromRequest( requestHeader: RequestHeader ):Try[UserInfo] = {
    Try{readCredentialFromRequest(requestHeader)}
  }

  def isDafSysAdmin( request:Request[Any]):Boolean ={
    val groups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${groups.toList}" )
    groups.contains(SysAdmin.toString)
  }

  def isOrgAdmin( request:Request[Any], orgName:String ):Boolean ={
    val groups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${groups.toList}" )
    groups.contains(Admin.toString+orgName)
  }

  def isOrgsAdmin( request:Request[Any], orgsNames:Seq[String] ):Boolean ={
    val userGroups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${userGroups.toList}" )
    (orgsNames.map(Admin.toString+_).toSet intersect userGroups.toSet).nonEmpty
  }

  def isAdminOfAllThisOrgs( request:Request[Any], orgsNames:Seq[String] ):Boolean ={
    val userGroups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${userGroups.toList}" )
    (orgsNames.map(Admin.toString+_).toSet intersect userGroups.toSet).size == orgsNames.length
  }

  def isOrgEditor( request:Request[Any], orgName:String ):Boolean ={
    val groups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${groups.toList}" )
    groups.contains(Editor.toString+orgName)
  }

  def isOrgsEditor( request:Request[Any], orgsNames:Seq[String] ):Boolean ={
    val userGroups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${userGroups.toList}" )
    (orgsNames.map(Editor.toString+_).toSet intersect userGroups.toSet).nonEmpty
  }

  def isBelongingToOrgAs( request:Request[Any], orgName:String ):Option[Role] ={
    val groups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${groups.toList}" )

    Role.pickRole(groups,orgName)
  }

  def getUserGroups( request:Request[Any]):Option[String] ={
    val groups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${groups.toList}" )

    Some(groups.mkString("|"))
  }


  def getUserAdminGroups( request:Request[Any]):Seq[String] ={

    val groups = readCredentialFromRequest(request).groups
    Logger.logger.info(s"belonging to groups: ${groups.toList}" )
    groups.filter(_.startsWith(Admin.toString)).map(_.replace(Admin.toString,""))

  }

}
