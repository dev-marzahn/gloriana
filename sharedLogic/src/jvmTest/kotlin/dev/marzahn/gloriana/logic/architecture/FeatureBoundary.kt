package dev.marzahn.gloriana.logic.architecture

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoImportDeclaration

private const val LOGIC_PACKAGE = "dev.marzahn.gloriana.logic"
private const val DATABASE_PACKAGE = "$LOGIC_PACKAGE.storage"

sealed interface BoundaryViolation {
  val fileName: String
  val storageType: String

  data class OutsideAFeature(override val fileName: String, override val storageType: String) : BoundaryViolation

  data class UsedByAnotherFeature(override val fileName: String, override val storageType: String) :
    BoundaryViolation
}

private class StorageType(val qualifiedName: String, packageName: String, val fileName: String) {
  val feature = featureOf(packageName)
}

fun featureBoundaryViolations(scope: KoScope): Set<BoundaryViolation> {
  val storageTypes = scope.classesAndInterfaces(includeNested = true)
    .filter { it.hasAnnotationOf(Entity::class, Dao::class) }
    .map { type ->
      StorageType(
        qualifiedName = type.fullyQualifiedName.orEmpty(),
        packageName = type.packagee?.name.orEmpty(),
        fileName = type.containingFile.nameWithExtension,
      )
    }
  val outsideAFeature = storageTypes
    .filter { it.feature == null }
    .map { BoundaryViolation.OutsideAFeature(it.fileName, it.qualifiedName) }
  val usedByAnotherFeature = scope.files.flatMap { file ->
    val fileFeature = featureOf(file.packagee?.name)
    val body = file.bodyOutsideTheDatabaseClass()
    storageTypes
      .filter { it.feature != null && it.feature != fileFeature && file.refersTo(it.qualifiedName, body) }
      .map { BoundaryViolation.UsedByAnotherFeature(file.nameWithExtension, it.qualifiedName) }
  }
  return (outsideAFeature + usedByAnotherFeature).toSet()
}

private fun featureOf(packageName: String?): String? =
  packageName?.takeIf { it.startsWith("$LOGIC_PACKAGE.") }
    ?.removePrefix("$LOGIC_PACKAGE.")
    ?.substringBefore('.')

private fun KoFileDeclaration.bodyOutsideTheDatabaseClass(): String {
  val body = text.lineSequence().filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")
  if (packagee?.name != DATABASE_PACKAGE) return body
  return classes()
    .filter { it.hasAnnotationOf(Database::class) }
    .fold(body) { remaining, database -> remaining.replace(database.text, "") }
}

private fun KoFileDeclaration.refersTo(qualifiedName: String, body: String): Boolean =
  body.containsWord(qualifiedName) ||
    imports.any { import -> import.nameInBody(qualifiedName)?.let(body::containsWord) == true }

private fun KoImportDeclaration.nameInBody(qualifiedName: String): String? {
  val imported = name.removeSuffix(".*")
  val rest = when {
    qualifiedName == imported -> ""
    qualifiedName.startsWith("$imported.") -> qualifiedName.removePrefix("$imported.")
    else -> null
  }
  return when {
    rest == null -> null
    isWildcard -> rest.ifEmpty { null }
    else -> listOf(alias?.name ?: imported.substringAfterLast('.'), rest).filter { it.isNotEmpty() }.joinToString(".")
  }
}

private fun String.containsWord(name: String) = Regex("(?<![\\w.])${Regex.escape(name)}\\b").containsMatchIn(this)
