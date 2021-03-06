/*
 * Copyright (C) 2005-2017 Qihoo 360 Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.qihoo360.replugin.gradle.host

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.qihoo360.replugin.gradle.compat.VariantCompat
import com.qihoo360.replugin.gradle.host.creator.FileCreators
import com.qihoo360.replugin.gradle.host.creator.IFileCreator
import com.qihoo360.replugin.gradle.host.creator.impl.json.PluginBuiltinJsonCreator
import com.qihoo360.replugin.gradle.host.handlemanifest.ComponentsGenerator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

/**
 * @author RePlugin Team
 */
public class Replugin implements Plugin<Project> {

    def static TAG = AppConstant.TAG
    def project
    def config

    @Override
    public void apply(Project project) {
        println "${TAG} Welcome to replugin world ! "

        this.project = project

        /* Extensions */
        project.extensions.create(AppConstant.USER_CONFIG, RepluginConfig)

        if (project.plugins.hasPlugin(AppPlugin)) {

            def android = project.extensions.getByType(AppExtension)
            android.applicationVariants.all { variant ->

                addShowPluginTask(variant)

                if (config == null) {
                    config = project.extensions.getByName(AppConstant.USER_CONFIG)
                    checkUserConfig(config)
                }

                def generateBuildConfigTask = VariantCompat.getGenerateBuildConfigTask(variant)
                def appID = generateBuildConfigTask.appPackageName
                def newManifest = ComponentsGenerator.generateComponent(appID, config)
                println "${TAG} countTask=${config.countTask}"

                def variantData = variant.variantData
                def scope = variantData.scope

                //host generate task
                def generateHostConfigTaskName = scope.getTaskName(AppConstant.TASK_GENERATE, "HostConfig")
                def generateHostConfigTask = project.task(generateHostConfigTaskName)

                generateHostConfigTask.doLast {
                    FileCreators.createHostConfig(project, variant, config)
                }
                generateHostConfigTask.group = AppConstant.TASKS_GROUP

                //depends on build config task
                if (generateBuildConfigTask) {
                    generateHostConfigTask.dependsOn generateBuildConfigTask
                    generateBuildConfigTask.finalizedBy generateHostConfigTask
                }

                //json generate task
                def generateBuiltinJsonTaskName = scope.getTaskName(AppConstant.TASK_GENERATE, "BuiltinJson")
                def generateBuiltinJsonTask = project.task(generateBuiltinJsonTaskName)

                generateBuiltinJsonTask.doLast {
                    FileCreators.createBuiltinJson(project, variant, config)
                }
                generateBuiltinJsonTask.group = AppConstant.TASKS_GROUP

                //depends on mergeAssets Task
                def mergeAssetsTask = VariantCompat.getMergeAssetsTask(variant)
                if (mergeAssetsTask) {
                    generateBuiltinJsonTask.dependsOn mergeAssetsTask
                    mergeAssetsTask.finalizedBy generateBuiltinJsonTask
                }

                variant.outputs.each { output ->
                    VariantCompat.getProcessManifestTask(output).doLast {
                        println "${AppConstant.TAG} processManifest: ${it.outputs.files}"
                        it.outputs.files.each { File file ->
                            updateManifest(file, newManifest)
                        }
                    }
                }
            }
        }
    }

    /**
     *
     * @hyongbai
     * 
     * ???gradle plugin 3.0.0?????????file???????????????????????????AndroidManifest.xml
     * ???gradle plugin 3.0.0?????????file????????????(?????????3.3.2)??????????????????????????????????????????????????? manifest ??????
     *
     * @param file manifest??????
     * @param newManifest ??????????????? manifest ??????
     */
    def updateManifest(def file, def newManifest) {
        // ???????????????AndroidManifest.xml???????????????????????????manifest-merger-debug-report.txt?????????????????????????????????
        if (file == null || !file.exists() || newManifest == null) return
        if (file.isDirectory()) {
            println "${AppConstant.TAG} updateManifest: ${file}"
            file.listFiles().each {
                updateManifest(it, newManifest)
            }
        } else if (file.name.equalsIgnoreCase("AndroidManifest.xml")) {
            appendManifest(file, newManifest)
        }
    }

    def appendManifest(def file, def content) {
        if (file == null || !file.exists()) return
        println "${AppConstant.TAG} appendManifest: ${file}"
        def updatedContent = file.getText("UTF-8").replaceAll("</application>", content + "</application>")
        file.write(updatedContent, 'UTF-8')
    }

    // ?????? ?????????????????????????????? ??????
    def addShowPluginTask(def variant) {
        def variantData = variant.variantData
        def scope = variantData.scope
        def showPluginsTaskName = scope.getTaskName(AppConstant.TASK_SHOW_PLUGIN, "")
        def showPluginsTask = project.task(showPluginsTaskName)

        showPluginsTask.doLast {
            IFileCreator creator = new PluginBuiltinJsonCreator(project, variant, config)
            def dir = creator.getFileDir()

            if (!dir.exists()) {
                println "${AppConstant.TAG} The ${dir.absolutePath} does not exist "
                println "${AppConstant.TAG} pluginsInfo=null"
                return
            }

            String fileContent = creator.getFileContent()
            if (null == fileContent) {
                return
            }

            new File(dir, creator.getFileName()).write(fileContent, 'UTF-8')
        }
        showPluginsTask.group = AppConstant.TASKS_GROUP

        //get mergeAssetsTask name, get real gradle task
        def mergeAssetsTask = VariantCompat.getMergeAssetsTask(variant)

        //depend on mergeAssetsTask so that assets have been merged
        if (mergeAssetsTask) {
            showPluginsTask.dependsOn mergeAssetsTask
        }

    }

    /**
     * ?????????????????????
     */
    def checkUserConfig(config) {
/*
        def persistentName = config.persistentName

        if (persistentName == null || persistentName.trim().equals("")) {
            project.logger.log(LogLevel.ERROR, "\n---------------------------------------------------------------------------------")
            project.logger.log(LogLevel.ERROR, " ERROR: persistentName can'te be empty, please set persistentName in replugin. ")
            project.logger.log(LogLevel.ERROR, "---------------------------------------------------------------------------------\n")
            System.exit(0)
            return
        }
*/
        doCheckConfig("countProcess", config.countProcess)
        doCheckConfig("countTranslucentStandard", config.countTranslucentStandard)
        doCheckConfig("countTranslucentSingleTop", config.countTranslucentSingleTop)
        doCheckConfig("countTranslucentSingleTask", config.countTranslucentSingleTask)
        doCheckConfig("countTranslucentSingleInstance", config.countTranslucentSingleInstance)
        doCheckConfig("countNotTranslucentStandard", config.countNotTranslucentStandard)
        doCheckConfig("countNotTranslucentSingleTop", config.countNotTranslucentSingleTop)
        doCheckConfig("countNotTranslucentSingleTask", config.countNotTranslucentSingleTask)
        doCheckConfig("countNotTranslucentSingleInstance", config.countNotTranslucentSingleInstance)
        doCheckConfig("countTask", config.countTask)

        println '--------------------------------------------------------------------------'
//        println "${TAG} appID=${appID}"
        println "${TAG} useAppCompat=${config.useAppCompat}"
        // println "${TAG} persistentName=${config.persistentName}"
        println "${TAG} countProcess=${config.countProcess}"

        println "${TAG} countTranslucentStandard=${config.countTranslucentStandard}"
        println "${TAG} countTranslucentSingleTop=${config.countTranslucentSingleTop}"
        println "${TAG} countTranslucentSingleTask=${config.countTranslucentSingleTask}"
        println "${TAG} countTranslucentSingleInstance=${config.countTranslucentSingleInstance}"
        println "${TAG} countNotTranslucentStandard=${config.countNotTranslucentStandard}"
        println "${TAG} countNotTranslucentSingleTop=${config.countNotTranslucentSingleTop}"
        println "${TAG} countNotTranslucentSingleTask=${config.countNotTranslucentSingleTask}"
        println "${TAG} countNotTranslucentSingleInstance=${config.countNotTranslucentSingleInstance}"

        println "${TAG} countTask=${config.countTask}"
        println '--------------------------------------------------------------------------'
    }

    /**
     * ???????????????????????????
     * @param name ?????????
     * @param count ?????????
     */
    def doCheckConfig(def name, def count) {
        if (!(count instanceof Integer) || count < 0) {
            this.project.logger.log(LogLevel.ERROR, "\n--------------------------------------------------------")
            this.project.logger.log(LogLevel.ERROR, " ${TAG} ERROR: ${name} must be an positive integer. ")
            this.project.logger.log(LogLevel.ERROR, "--------------------------------------------------------\n")
            System.exit(0)
        }
    }
}

class RepluginConfig {

    /** ????????????????????????(??? UI ??? Persistent ??????) */
    def countProcess = 3

    /** ??????????????????????????? */
    def persistentEnable = true

    /** ?????????????????????????????????????????? Persistent ?????????????????????????????????*/
    def persistentName = ':GuardService'

    /** ?????????????????????????????? */
    def countNotTranslucentStandard = 6
    def countNotTranslucentSingleTop = 2
    def countNotTranslucentSingleTask = 3
    def countNotTranslucentSingleInstance = 2

    /** ??????????????????????????? */
    def countTranslucentStandard = 2
    def countTranslucentSingleTop = 2
    def countTranslucentSingleTask = 2
    def countTranslucentSingleInstance = 3

    /** ?????????????????? TaskAffinity ????????? */
    def countTask = 2

    /**
     * ???????????? AppCompat ???
     * com.android.support:appcompat-v7:25.2.0
     */
    def useAppCompat = false

    /** HOST ??????????????????????????? */
    def compatibleVersion = 10

    /** HOST ???????????? */
    def currentVersion = 12

    /** plugins-builtin.json ??????????????????,????????? "plugins-builtin.json" */
    def builtInJsonFileName = "plugins-builtin.json"

    /** ?????????????????? plugins-builtin.json ??????,?????????????????? */
    def autoManageBuiltInJsonFile = true

    /** assert?????????????????????????????????????????????,????????? assert ??? "plugins" */
    def pluginDir = "plugins"

    /** ??????????????????????????????,?????????".jar" ???????????? jar ??????*/
    def pluginFilePostfix = ".jar"

    /** ???????????????????????????????????????????????? jar (???????????????????????? jar)?????????????????????,????????? true */
    def enablePluginFileIllegalStopBuild = true
}
