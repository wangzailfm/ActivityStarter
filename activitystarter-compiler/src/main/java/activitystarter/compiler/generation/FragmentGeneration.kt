package activitystarter.compiler.generation

import activitystarter.compiler.model.classbinding.ClassModel
import activitystarter.compiler.model.param.ArgumentModel
import activitystarter.compiler.utils.BUNDLE
import activitystarter.compiler.utils.doIf
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec

internal class FragmentGeneration(classModel: ClassModel) : ClassGeneration(classModel) {

    override fun createFillFieldsMethod() = getBasicFillMethodBuilder()
            .addParameter(classModel.targetTypeName, "fragment")
            .doIf(classModel.argumentModels.isNotEmpty()) {
                addFieldSettersCode()
            }
            .build()!!

    override fun createStarters(variant: List<ArgumentModel>): List<MethodSpec> = listOf(
            createGetFragmentMethod(variant)
    )

    override fun TypeSpec.Builder.addExtraToClass() = this
            .addMethod(createSaveMethod())
            .addNoSettersAccessors()

    private fun MethodSpec.Builder.addFieldSettersCode() {
        addStatement("\$T arguments = fragment.getArguments()", BUNDLE)
        addBundleSetters("arguments", "fragment", true)
    }

    private fun createGetFragmentMethod(variant: List<ArgumentModel>) = builderWithCreationBasicFieldsNoContext("newInstance")
            .addArgParameters(variant)
            .returns(classModel.targetTypeName)
            .addGetFragmentCode(variant)
            .build()

    private fun MethodSpec.Builder.addGetFragmentCode(variant: List<ArgumentModel>) = this
            .addStatement("\$T fragment = new \$T()", classModel.targetTypeName, classModel.targetTypeName)
            .doIf(variant.isNotEmpty()) {
                addStatement("\$T args = new Bundle()", BUNDLE)
                addSaveBundleStatements("args", variant, { it.name })
                addStatement("fragment.setArguments(args)")
            }
            .addStatement("return fragment")

    private fun createSaveMethod(): MethodSpec = this
            .builderWithCreationBasicFieldsNoContext("save")
            .addParameter(classModel.targetTypeName, "fragment")
            .doIf(classModel.savable) {
                addStatement("\$T bundle = new Bundle()", BUNDLE)
                addSaveBundleStatements("bundle", classModel.argumentModels, { "fragment.${it.accessor.makeGetter()}" })
                addStatement("Bundle arguments = fragment.getArguments()")
                addCode("if (arguments == null) {")
                addStatement("    fragment.setArguments(bundle)")
                addCode("} else {")
                addStatement("    arguments.putAll(bundle)")
                addCode("}")
            }
            .build()

    private fun TypeSpec.Builder.addNoSettersAccessors(): TypeSpec.Builder = apply {
        classModel.argumentModels.filter { it.noSetter }.forEach { arg ->
            addMethod(buildCheckValueMethod(arg))
            addMethod(buildGetValueMethod(arg))
        }
    }

    private fun buildCheckValueMethod(arg: ArgumentModel): MethodSpec? = builderWithCreationBasicFieldsNoContext(arg.checkerName)
            .addParameter(classModel.targetTypeName, "fragment")
            .returns(TypeName.BOOLEAN)
            .addStatement("\$T bundle = fragment.getArguments()", BUNDLE)
            .addStatement("return bundle.containsKey(${arg.keyFieldName})")
            .build()

    private fun buildGetValueMethod(arg: ArgumentModel): MethodSpec? = builderWithCreationBasicFieldsNoContext(arg.accessorName)
            .addParameter(classModel.targetTypeName, "fragment")
            .returns(arg.typeName)
            .buildGetValueMethodBody(arg)
            .build()

    private fun MethodSpec.Builder.buildGetValueMethodBody(arg: ArgumentModel) = apply {
        addStatement("\$T arguments = fragment.getArguments()", BUNDLE)
        val fieldName = arg.keyFieldName
        val bundleValue = (if (arg.paramType.typeUsedBySupertype()) "(\$T) " else "") +
                arg.addUnwrapper { getBundleGetter("arguments", arg.saveParamType, fieldName) }
        addStatement("return $bundleValue", arg.typeName)
    }
}
