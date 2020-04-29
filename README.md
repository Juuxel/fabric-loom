# Fabric Loom - Sin² Edition
A fork of [Fabric's Gradle plugin](https://github.com/FabricMC/fabric-loom) to make it do things asie didn't want it to do.

Usage: `gradlew genSources eclipse/idea/vscode`
(Use `./gradle` on macOS and Linux)


## What's new?
* [FernFlower](https://github.com/FabricMC/intellij-fernflower) switched to [ForgeFlower](https://github.com/MinecraftForge/ForgeFlower) for `genSources`
* Support for using mappings on the wrong version
* Support for Enigma mappings
* Support for gz compressed Tiny mappings
* Support to pull Enigma mappings straight from Github
* Support for dynamically defined mappings directly in `build.gradle`
* Support to stack mappings on top of each other
* Access Transformers
* Easier additional remapped jar tasks
* Optional non-forking decompiling for `genSources`
* Guaranteed Gradle 4.9 support


## What do I need to change?
Whilst not a whole lot needs to change compared to a normal Loom setup, there is a single tweak that has to be made in order to get said setup running. A full example of a working `build.gradle` using several Sin² features can be found [here](https://github.com/Chocohead/Fabric-ASM/blob/master/build.gradle).

### Declaring the plugin
The Jitpack maven is needed to grab [ForgeFlower](https://github.com/Chocohead/ForgedFlower), a [Tiny Remapper fork](https://github.com/Chocohead/tiny-remapper), and a [Tiny Mappings Parser fork](https://github.com/Chocohead/Tiny-Mappings-Parser) in order for Sin² to work. Fabric's maven will cover all other libraries that both Loom and Sin² need to work aside from [Darcula](https://github.com/bulenkov/Darcula) which is on JCenter. The Gradle plugin also needs to change in order to pull the right version of Loom. Sin² versions are marked by the short Git commit revision.

Together, the following will need to be switched in `build.gradle`:
```groovy
buildscript {
	repositories {
		jcenter()
		maven {
			name = "Fabric"
			url = "https://maven.fabricmc.net/"
		}
		maven { 
			name = "Jitpack"
			url = "https://jitpack.io/"
		}
	}
	dependencies {
		//Sin² Edition Loom
		classpath 'com.github.Chocohead:fabric-loom:e131f8f'
	}
}
plugins {
	//Old/normal Loom plugin (comment this out or remove the line entirely)
	//id 'fabric-loom' version '0.2.6-SNAPSHOT'
	...
}
apply plugin: "fabric-loom"
```

### Which branch do I use?
Each branch is based on an upstream version of Loom (see table below); the most recent commit a branch has is likeliest the best one to use. When swapping between Loom forks, aiming to match like for like versions minimises how much has to change in your `build.gradle` in one go (and thus how much can go wrong). Features are not always backported however so it might prove prudent to update forwards if a feature you need is missing. Any problems or backport requests can be made [here](https://github.com/Chocohead/fabric-loom/issues).

Stock Version | Sin² Branch | Example Sin² Version
:---: | :---: | :---:
0.1.0 | [sin](https://github.com/Chocohead/fabric-loom/tree/sin) | **3c39479**
0.1.1 | *\<None\>* | -
0.2.0 | [*\<Floating\>*](https://github.com/Chocohead/fabric-loom/compare/3c39479...f7f4a45) | **2665770** to **f7f4a45**
0.2.1 | [ATs](https://github.com/Chocohead/fabric-loom/tree/ATs) | **89a5973**
0.2.2 | [sin²](https://github.com/Chocohead/fabric-loom/tree/sin²) | **51f7373**
0.2.3 | [*\<Floating\>*](https://github.com/Chocohead/fabric-loom/compare/f2fc524...32e0cc5) | **c4551b3** and **32e0cc5**
0.2.4 | [openfine](https://github.com/Chocohead/fabric-loom/tree/openfine) | **7eb4201**
0.2.5 | [dust](https://github.com/Chocohead/fabric-loom/tree/dust) | **5784f06**
0.2.6 | [leaf](https://github.com/Chocohead/fabric-loom/tree/leaf) | **1fc286d**
0.2.7 | *\<None\>* | -
0.4.x | *\<None\>* | -


## How do I use the new things?
Once you've switched over to using Sin², ForgeFlower decompiling will be used for `genSources`. For the other additional features however, more changes are needed:

### Running mappings on different versions
Normally it is up to the mappings to declare the Minecraft version they are designed for, and Loom will just trust that they supply everything that is needed remapping wise. This can cause a problem when trying to use them on another Minecraft version as they could be missing mappings for new or changed parts of the code. Sin² instead only trusts the mappings if they were designed for the Minecraft version being used, otherwise it will grab the correct Intermediary mappings for the Minecraft version actually being used and apply the mappings on top, allowing for the mappings to be missing parts without issue.

### Running with Enigma mappings
Tiny V1 files don't ship with parameter or local variable names, whilst like Tiny V2 mappings, Enigma ones do. Thus for older versions without Tiny V2 files in order to get parameter mappings for methods, Enigma mappings have to be used instead. This causes additional excitement as the Enigma mappings don't come with [Intermediary mappings](https://github.com/FabricMC/intermediary). Fortunately this is all handled in the background and the additional Intermediaries will be downloaded if needed for the version of Minecraft being used. Several more steps will be noticed in the build process as a result as the two mapping sets then need to be merged and rewritten to the expected Tiny format used later by Loom. This only needs to happen once every time the mappings are changed though, so it's not so bad.

If previously the mappings dependency looked like
```groovy
mappings "net.fabricmc:yarn:19w13a.2"
```
In order to use the Enigma version, it would need to be changed to
```groovy
mappings "net.fabricmc:yarn:19w13a.2:enigma@zip"
```
Nothing else is required, when the project is next evaluated the change will be detected by the lack of a method parameters file and thus the mappings rebuilt. In theory at least, it's normally quite good at behaving.

Note that Enigma mappings have not been exported to the Fabric maven as part of Yarn since 1.14.3 (as removed in [The Great Intermediary Update](https://github.com/FabricMC/yarn/commit/1a6f261a2ebd5ab5c5572489ceea54ecc5a2ae74#diff-c197962302397baf3a4cc36463dce5eaL487-L489) before 1.14.4-pre1). Tiny V2 mappings are available since 1.14.4-pre1 instead however.


### Running with gz compressed Tiny mappings
Whilst not making that much of a difference in the grand scheme of things, using the compressed Tiny mappings over the normal jar distribution does save you an entire kilobyte of downloading. It's the thought that counts really.

If previously the mappings dependency looked like
```groovy
mappings "net.fabricmc:yarn:19w13a.2"
```
In order to use the compressed form, it would need to be changed to
```groovy
mappings "net.fabricmc:yarn:19w13a.2:tiny@gz"
```
Fairly simple stuff, just like with Enigma. Only without the obvious benefits.


### Running with Enigma mappings from Github
Using Enigma mappings is all well and good, parameter names and all, but it does rely on the zip being hosted on a maven in order to be downloaded. Fortunately, Sin² offers a way of downloading mappings straight from the [Yarn repo](https://github.com/FabricMC/yarn) or indeed any other Github repository directly. This means any pull request you might want to try you can before it is pulled into the main repo. As well as using the main repo's Enigma mappings at all given they're not exported anymore.

If previously the mappings dependency looked like
```groovy
mappings "net.fabricmc:yarn:19w13a.1"
```
In order to use the Github mappings, it would need to be changed to
```groovy
mappings loom.yarnBranch("19w13a") {spec ->
	spec.version = "19w13a-1"
}
//or
mappings loom.yarnCommit("6e610a8") {spec ->
	spec.version = "19w40a-1"
}
//or even
mappings loom.fromBranch("MyOrg/Repo", "myBranch") {spec ->
	spec.group = "my.great.group" //Is the user/organisation's name by default
	spec.name = "Best-Mappings" //Is the repository's name by default
	spec.version = "1.14.4-3"

	spec.forceFresh = true //Force the mappings to be redownloaded even when they haven't changed
}
```
Explicitly forcing the version is important to ensure the correct Intermediaries are chosen, it also allows versioning commits/branches that would otherwise be impossible to update between without changing the mapping group or name. Note that any changed to a chosen branch will be picked up and downloaded when Gradle is run (similar to a `-SNAPSHOT` version), commits however are completely stable even if forced over in the repository's tree.


### Dynamic mappings
For the times where a modest number of mappings might be needed, or mappings otherwise dynamically changing in a way that makes a file inconvenient, dynamic mappings provide a solution. Defined directly in the `build.gradle` `dependencies` block, dynamic mappings allow any number of class, method or field mappings to be added from and to any desired namespace (defaulting to `intermediary` and `named` respectively):
```groovy
dependencies {
	//Minecraft version is supplied to provide context for the version the mappings are designed for
	//Only taken into account practically if one of the namespaces is official
	mappings loom.extraMappings("1.15.2") {mappings ->
		//Default namespaces for the mappings, not needed to be specified if not changed
		mappings.from = "intermediary"
		mappings.to = "named"

		//Add a class mapping
		mappings.class "net/minecraft/class_1768", "net/minecraft/item/DyeableItem"
		//Add a field mapping
		mappings.field "net/minecraft/class_2586", "Z", "field_11865", "removed"
		//Add a method mapping
		mappings.method "net/minecraft/class_310", "()Z", "method_1542", "isInSingleplayer"
	}
	...
}
```
Each dynamic mapping block is independent of any other defined in the `build.gradle`. Any changes to a dynamic mapping will be reflected as soon as Gradle is run again, akin to changing any other mapping dependency, remapping the Minecraft jar and any dependency mods.

The dependency group of all dynamic mappings is `net.fabricmc.synthetic.extramappings`; whilst the name is a [Murmur3 128-bit hash](https://en.wikipedia.org/wiki/MurmurHash#MurmurHash3) of the contents so that identical blocks can reuse the same underlying compressed file storage (and so they don't conflict with each other).


### Stacking mappings
Having a single mapping file is ideal for mapping the game to a single set of mappings designed for a single Minecraft version. But for cross version work where newer/older mappings are desirable, older/newer things may be missing names. Previously these names would have to be manually added to a new mappings file and that used. Instead Sin² allows stacking mappings together to cascade names from the provided files.

This is done by allowing as many `mappings` dependencies to be declared as desired. If one is supplied Sin² acts as stock Loom would. If no mapping is supplied the game is only mapped to Intermediary names. If more than one is supplied, the first found name from the provided mappings is used for every class, method and field for the given Minecraft version. This means a subsequent mapping file can provide names where an earlier one is missing them, without replacing the ones said earlier one already had. The mapping files can be for any Minecraft version as any Intermediaries that need to be downloaded for direct `official` to `named` mappings will be downloaded automatically.

Taken as a practical example:
```groovy
dependencies {
	minecraft "com.mojang:minecraft:1.15.2" //We want to be naming 1.15.2

	//Some names conflict between the older and newer mappings
	//So these are explicitly arbitrated over here to ensure there are no conflicts
	mappings loom.extraMappings("1.15.2") {mappings ->
		mappings.class "net/minecraft/class_1768", "net/minecraft/item/DyeableItem"
		mappings.class "net/minecraft/class_332", "net/minecraft/client/gui/DrawableHelper"
		mappings.class "net/minecraft/class_339", "net/minecraft/client/gui/widget/AbstractButtonWidget"
		mappings.class "net/minecraft/class_280", "net/minecraft/client/gl/JsonGlProgram"
		mappings.field "net/minecraft/class_3244", "I", "field_14137", "vehicleFloatingTicks"
		mappings.field "net/minecraft/class_2586", "Z", "field_11865", "removed"
		mappings.method "net/minecraft/class_8", "(Lnet/minecraft/class_1922;III)Lnet/minecraft/class_7;", "method_25", "getNodeType"
		mappings.method "net/minecraft/class_259", "(Lnet/minecraft/class_265;Lnet/minecraft/class_265;Lnet/minecraft/class_247;)Lnet/minecraft/class_265;", "method_1072", "combineAndSimplify"
		mappings.method "net/minecraft/class_265", "(Lnet/minecraft/class_2350;)Lnet/minecraft/class_265;", "method_1098", "getUnchachedFace"
		mappings.method "net/minecraft/class_276", "(IIZ)V", "method_1233", "drawInternal"
		mappings.method "net/minecraft/class_1959", "(Lnet/minecraft/class_2338;)F", "method_8707", "computeTemperature"
		mappings.method "net/minecraft/class_1914", "()Lnet/minecraft/class_1799;", "method_8250", "getMutableSellItem"
		mappings.method "net/minecraft/class_1665", "()Lnet/minecraft/class_3414;", "method_7440", "getHitSound"
		mappings.method "net/minecraft/class_1408", "()Z", "method_6343", "shouldRecalculatePath"
		mappings.method "net/minecraft/class_1060", "(Lnet/minecraft/class_2960;)V", "method_4618", "bindTextureInner"
		mappings.method "net/minecraft/class_342", "(I)V", "method_1875", "setSelectionStart"
		mappings.method "net/minecraft/class_310", "()Z", "method_1542", "isInSingleplayer"
		mappings.method "net/minecraft/server/MinecraftServer", "(Ljava/util/function/BooleanSupplier;)V", "method_3813", "tickWorlds"
	}
	//Use the build 100 18w50a mappings where possible
	mappings "net.fabricmc:yarn:18w50a.100"
	//Otherwise use the 1.15.2 mappings
	mappings "net.fabricmc:yarn:1.15.2+build.14:v2"
}
```
As seen, stacking mappings can result in conflicts which prevent remapping. Using a dynamic mapping before the conflicting files is the best strategy to correct for this, as any mappings specified will take precedent and avoid the later conflicting mappings from being used. Once corrective steps have been taken to resolve the conflicts the Minecraft jar will be remapped again once Gradle is run.

All conflicts are given as a single list and the build will stop there until they are manually corrected. This can be bypassed for method and field naming conflicts by adding the appropriate flag in the `minecraft` block:
```groovy
minecraft {
	//Not especially recommended if there are wide spread conflicts, but works for a quick fix
	bulldozeMappings = true
}
```
Conflicting class names cannot be bulldozed however as there would be no way of loading two identically named classes. Any (strange) issues that arise from bulldozing mappings should be taken as a warning that this has resulted in uncorrectable problems with the remapped jars. Hence this is not a recommended fix for long term projects.


### Access Transformers
Sin² provides dev time access transformations for making Minecraft classes and methods public (and non-final). For an explanation of how to use this, as well as the runtime component for using the ATs in game, see [here](https://github.com/Chocohead/Fabric-ASM#sailing-the-shenanigans).


### Additional tasks
Sin² adds an additional task type for producing remapping jars from other source sets on top of what the default `jar` task makes. [`RemappingJar`](https://github.com/Chocohead/fabric-loom/blob/ATs/src/main/java/net/fabricmc/loom/task/RemappingJar.java) is an extension of the normal `Jar` task which both remaps the output, and can optionally include the access transformer for the project:
```groovy
task exampleJar(type: RemappingJar, dependsOn: exampleClasses) {
	from sourceSets.example.output
	includeAT = false
}
```
The example source set will now produce a separate jar which doesn't include the (remapped) access transformer file. Like the normal `Jar` task as many files can be added to the compilation set as desired.


## What's broken?
With the newer 1.16 snapshots there are a couple of methods which ForgeFlower is unable to decompile. The various `register` methods in `net/minecraft/data/client/model/BlockStateVariantMap` and one in `BlockStateModelGenerator` all fail with a `StackOverflowError` (which can fill the console out depending on the logging history). `net/minecraft/entity/ai/brain/Brain#getOptionalMemory` also fails to decompile with a `NullPointerException`.

Other than those issues (which manifest themselves as warnings whilst running `genSources`), there is nothing else Sin² knowingly breaks. Feel free to [report](https://github.com/Chocohead/Fabric-Loom/issues) anything if you do find something.
