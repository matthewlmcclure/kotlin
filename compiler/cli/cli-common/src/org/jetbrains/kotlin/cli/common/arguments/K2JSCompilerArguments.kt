/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.cli.common.arguments

import org.jetbrains.kotlin.cli.common.arguments.K2JsArgumentConstants.*
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.AnalysisFlags.allowFullyQualifiedNameInKClass

class K2JSCompilerArguments : CommonCompilerArguments() {
    companion object {
        @JvmStatic private val serialVersionUID = 0L
    }

    @GradleOption(
        value = DefaultValues.StringNullDefault::class,
        gradleInputType = GradleInputTypes.INTERNAL // handled by task 'outputFileProperty'
    )
    @GradleDeprecatedOption(
        message = "Only for legacy backend. For IR backend please use task.destinationDirectory and moduleName",
        level = DeprecationLevel.WARNING,
        removeAfter = "1.9.0"
    )
    @Argument(value = "-output", valueDescription = "<filepath>", description = "Destination *.js file for the compilation result")
    var outputFile: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xir-output-dir", valueDescription = "<directory>", description = "Destination for generated files")
    var outputDir: String? by NullableStringFreezableVar(null)

    @GradleOption(
        value = DefaultValues.StringNullDefault::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "-Xir-module-name", description = "Base name of generated files")
    var moduleName: String? by NullableStringFreezableVar(null)

    @GradleOption(
        value = DefaultValues.BooleanTrueDefault::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "-no-stdlib", description = "Don't automatically include the default Kotlin/JS stdlib into compilation dependencies")
    var noStdlib: Boolean by FreezableVar(false)

    @Argument(
            value = "-libraries",
            valueDescription = "<path>",
            description = "Paths to Kotlin libraries with .meta.js and .kjsm files, separated by system path separator"
    )
    var libraries: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-Xrepositories",
        valueDescription = "<path>",
        description = "Paths to additional places where libraries could be found"
    )
    var repositries: String? by NullableStringFreezableVar(null)

    @GradleOption(
        value = DefaultValues.BooleanFalseDefault::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "-source-map", description = "Generate source map")
    var sourceMap: Boolean by FreezableVar(false)

    @GradleOption(
        value = DefaultValues.StringNullDefault::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "-source-map-prefix", description = "Add the specified prefix to paths in the source map")
    var sourceMapPrefix: String? by NullableStringFreezableVar(null)

    @Argument(
            value = "-source-map-base-dirs",
            deprecatedName = "-source-map-source-roots",
            valueDescription = "<path>",
            description = "Base directories for calculating relative paths to source files in source map"
    )
    var sourceMapBaseDirs: String? by NullableStringFreezableVar(null)

    /**
     * SourceMapEmbedSources should be null by default, since it has effect only when source maps are enabled.
     * When sourceMapEmbedSources are not null and source maps is disabled warning is reported.
     */
    @GradleOption(
        value = DefaultValues.JsSourceMapContentModes::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(
            value = "-source-map-embed-sources",
            valueDescription = "{always|never|inlining}",
            description = "Embed source files into source map"
    )
    var sourceMapEmbedSources: String? by NullableStringFreezableVar(null)

    @GradleOption(
        value = DefaultValues.BooleanTrueDefault::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "-meta-info", description = "Generate .meta.js and .kjsm files with metadata. Use to create a library")
    var metaInfo: Boolean by FreezableVar(false)

    @GradleOption(
        value = DefaultValues.JsEcmaVersions::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "-target", valueDescription = "{ v5 }", description = "Generate JS files for specific ECMA version")
    var target: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-Xir-keep",
        description = "Comma-separated list of fully-qualified names to not be eliminated by DCE (if it can be reached), " +
                "and for which to keep non-minified names."
    )
    var irKeep: String? by NullableStringFreezableVar(null)

    @GradleOption(
        value = DefaultValues.JsModuleKinds::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(
            value = "-module-kind",
            valueDescription = "{plain|amd|commonjs|umd}",
            description = "Kind of the JS module generated by the compiler"
    )
    var moduleKind: String? by NullableStringFreezableVar(K2JsArgumentConstants.MODULE_PLAIN)

    @GradleOption(
        value = DefaultValues.JsMain::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(
        value = "-main",
        valueDescription = "{$CALL|$NO_CALL}",
        description = "Define whether the `main` function should be called upon execution")
    var main: String? by NullableStringFreezableVar(null)

    @Argument(
            value = "-output-prefix",
            valueDescription = "<path>",
            description = "Add the content of the specified file to the beginning of output file"
    )
    var outputPrefix: String? by NullableStringFreezableVar(null)

    @Argument(
            value = "-output-postfix",
            valueDescription = "<path>",
            description = "Add the content of the specified file to the end of output file"
    )
    var outputPostfix: String? by NullableStringFreezableVar(null)

    // Advanced options

    @Argument(
        value = "-Xir-produce-klib-dir",
        description = "Generate unpacked KLIB into parent directory of output JS file.\n" +
                "In combination with -meta-info generates both IR and pre-IR versions of library."
    )
    var irProduceKlibDir: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xir-produce-klib-file",
        description = "Generate packed klib into file specified by -output. Disables pre-IR backend"
    )
    var irProduceKlibFile: Boolean by FreezableVar(false)

    @Argument(value = "-Xir-produce-js", description = "Generates JS file using IR backend. Also disables pre-IR backend")
    var irProduceJs: Boolean by FreezableVar(false)

    @Argument(value = "-Xir-dce", description = "Perform experimental dead code elimination")
    var irDce: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xir-dce-runtime-diagnostic",
        valueDescription = "{$RUNTIME_DIAGNOSTIC_LOG|$RUNTIME_DIAGNOSTIC_EXCEPTION}",
        description = "Enable runtime diagnostics when performing DCE instead of removing declarations"
    )
    var irDceRuntimeDiagnostic: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xir-dce-print-reachability-info", description = "Print declarations' reachability info to stdout during performing DCE")
    var irDcePrintReachabilityInfo: Boolean by FreezableVar(false)

    @Argument(value = "-Xir-property-lazy-initialization", description = "Perform lazy initialization for properties")
    var irPropertyLazyInitialization: Boolean by FreezableVar(true)

    @Argument(value = "-Xir-minimized-member-names", description = "Perform minimization for names of members")
    var irMinimizedMemberNames: Boolean by FreezableVar(false)

    @Argument(value = "-Xir-only", description = "Disables pre-IR backend")
    var irOnly: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xir-klib-module-name",
        valueDescription = "<name>",
        description = "Specify a compilation module name for IR backend"
    )
    var irModuleName: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xir-base-class-in-metadata", description = "Write base class into metadata")
    var irBaseClassInMetadata: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xir-safe-external-boolean",
        description = "Safe access via Boolean() to Boolean properties in externals to safely cast falsy values."
    )
    var irSafeExternalBoolean: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xir-safe-external-boolean-diagnostic",
        valueDescription = "{$RUNTIME_DIAGNOSTIC_LOG|$RUNTIME_DIAGNOSTIC_EXCEPTION}",
        description = "Enable runtime diagnostics when access safely to boolean in external declarations"
    )
    var irSafeExternalBooleanDiagnostic: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xir-per-module", description = "Splits generated .js per-module")
    var irPerModule: Boolean by FreezableVar(false)

    @Argument(value = "-Xir-per-module-output-name", description = "Adds a custom output name to the splitted js files")
    var irPerModuleOutputName: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xir-per-file", description = "Splits generated .js per-file")
    var irPerFile: Boolean by FreezableVar(false)

    @Argument(value = "-Xir-new-ir2js", description = "New fragment-based ir2js")
    var irNewIr2Js: Boolean by FreezableVar(true)

    @Argument(
        value = "-Xir-generate-inline-anonymous-functions",
        description = "Lambda expressions that capture values are translated into in-line anonymous JavaScript functions"
    )
    var irGenerateInlineAnonymousFunctions: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xinclude",
        valueDescription = "<path>",
        description = "A path to an intermediate library that should be processed in the same manner as source files."
    )
    var includes: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-Xcache-directories",
        valueDescription = "<path>",
        description = "A path to cache directories"
    )
    var cacheDirectories: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xir-build-cache", description = "Use compiler to build cache")
    var irBuildCache: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xgenerate-dts",
        description = "Generate TypeScript declarations .d.ts file alongside JS file. Available in IR backend only."
    )
    var generateDts: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xstrict-implicit-export-types",
        description = "Generate strict types for implicitly exported entities inside d.ts files. Available in IR backend only."
    )
    var strictImplicitExportType: Boolean by FreezableVar(false)

    @GradleOption(
        value = DefaultValues.BooleanTrueDefault::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "-Xtyped-arrays", description = "Translate primitive arrays to JS typed arrays")
    var typedArrays: Boolean by FreezableVar(true)

    @GradleOption(
        value = DefaultValues.BooleanFalseDefault::class,
        gradleInputType = GradleInputTypes.INPUT
    )
    @Argument(value = "-Xfriend-modules-disabled", description = "Disable internal declaration export")
    var friendModulesDisabled: Boolean by FreezableVar(false)

    @Argument(
            value = "-Xfriend-modules",
            valueDescription = "<path>",
            description = "Paths to friend modules"
    )
    var friendModules: String? by NullableStringFreezableVar(null)

    @Argument(
        value = "-Xenable-extension-functions-in-externals",
        description = "Enable extensions functions members in external interfaces"
    )
    var extensionFunctionsInExternals: Boolean by FreezableVar(false)

    @Argument(value = "-Xmetadata-only", description = "Generate *.meta.js and *.kjsm files only")
    var metadataOnly: Boolean by FreezableVar(false)

    @Argument(value = "-Xenable-js-scripting", description = "Enable experimental support of .kts files using K/JS (with -Xir only)")
    var enableJsScripting: Boolean by FreezableVar(false)

    @Argument(value = "-Xfake-override-validator", description = "Enable IR fake override validator")
    var fakeOverrideValidator: Boolean by FreezableVar(false)

    @Argument(value = "-Xerror-tolerance-policy", description = "Set up error tolerance policy (NONE, SEMANTIC, SYNTAX, ALL)")
    var errorTolerancePolicy: String? by NullableStringFreezableVar(null)

    @Argument(value = "-Xpartial-linkage", description = "Allow unlinked symbols")
    var partialLinkage: Boolean by FreezableVar(false)

    @Argument(value = "-Xwasm", description = "Use experimental WebAssembly compiler backend")
    var wasm: Boolean by FreezableVar(false)

    @Argument(value = "-Xwasm-debug-info", description = "Add debug info to WebAssembly compiled module")
    var wasmDebug: Boolean by FreezableVar(true)

    @Argument(value = "-Xwasm-kclass-fqn", description = "Enable support for FQ names in KClass")
    var wasmKClassFqn: Boolean by FreezableVar(false)

    @Argument(value = "-Xwasm-enable-array-range-checks", description = "Turn on range checks for the array access functions")
    var wasmEnableArrayRangeChecks: Boolean by FreezableVar(false)

    @Argument(value = "-Xwasm-enable-asserts", description = "Turn on asserts")
    var wasmEnableAsserts: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xuse-deprecated-legacy-compiler",
        description = "Use deprecated legacy compiler without error"
    )
    var useDeprecatedLegacyCompiler: Boolean by FreezableVar(false)

    @Argument(
        value = "-Xlegacy-deprecated-no-warn",
        description = "Disable warnings of deprecation of legacy compiler"
    )
    var legacyDeprecatedNoWarn: Boolean by FreezableVar(false)

    override fun configureAnalysisFlags(collector: MessageCollector, languageVersion: LanguageVersion): MutableMap<AnalysisFlag<*>, Any> {
        return super.configureAnalysisFlags(collector, languageVersion).also {
            it[allowFullyQualifiedNameInKClass] = wasm && wasmKClassFqn //Only enabled WASM BE supports this flag
        }
    }

    override fun checkIrSupport(languageVersionSettings: LanguageVersionSettings, collector: MessageCollector) {
        if (!isIrBackendEnabled()) return

        if (languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_4
            || languageVersionSettings.apiVersion < ApiVersion.KOTLIN_1_4
        ) {
            collector.report(
                CompilerMessageSeverity.ERROR,
                "IR backend cannot be used with language or API version below 1.4"
            )
        }
    }

    override fun configureLanguageFeatures(collector: MessageCollector): MutableMap<LanguageFeature, LanguageFeature.State> {
        return super.configureLanguageFeatures(collector).apply {
            if (extensionFunctionsInExternals) {
                this[LanguageFeature.JsEnableExtensionFunctionInExternals] = LanguageFeature.State.ENABLED
            }
            if (!isIrBackendEnabled()) {
                this[LanguageFeature.JsAllowInvalidCharsIdentifiersEscaping] = LanguageFeature.State.DISABLED
            }
            if (isIrBackendEnabled()) {
                this[LanguageFeature.JsAllowValueClassesInExternals] = LanguageFeature.State.ENABLED
            }
        }
    }
}

fun K2JSCompilerArguments.isPreIrBackendDisabled(): Boolean =
    irOnly || irProduceJs || irProduceKlibFile || irBuildCache

fun K2JSCompilerArguments.isIrBackendEnabled(): Boolean =
    irProduceKlibDir || irProduceJs || irProduceKlibFile || wasm || irBuildCache
