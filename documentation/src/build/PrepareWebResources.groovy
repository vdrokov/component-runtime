/**
 *  Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.talend.sdk.component.build

import org.asciidoctor.Asciidoctor
import org.asciidoctor.AttributesBuilder
import org.asciidoctor.OptionsBuilder
import org.asciidoctor.SafeMode

import javax.json.bind.JsonbBuilder
import javax.json.bind.JsonbConfig
import javax.json.bind.config.PropertyOrderStrategy

import org.jsoup.Jsoup

def adoc = Asciidoctor.Factory.create()

def copyJsResource = { source, output ->
    def target = new File(project.build.directory, "${project.build.finalName}/_/${output}")
    target.parentFile.mkdirs()
    target.text = source.text
    log.info("Imported ${target.name}")
}

// add web resources
copyJsResource(
    new File(project.basedir, 'src/main/frontend/node_modules/highlight.js/styles/idea.css').toURI().toURL(),
    'css/idea.css')
copyJsResource(
    new File(project.basedir, 'src/main/frontend/node_modules/js-search/dist/umd/js-search.min.js').toURI().toURL(),
    'js/js-search.min.js')

// temp antora patch to add the branch
def antoraLoadAdoc = new File(project.basedir, 'src/main/frontend/node_modules/@antora/asciidoc-loader/lib/load-asciidoc.js')
if (!antoraLoadAdoc.text.contains('docbranch')) {
    antoraLoadAdoc.text = antoraLoadAdoc.text.replace(
            'const intrinsicAttrs = {',
            'const intrinsicAttrs = {docbranch: (file.origin || (file.src || {}).origin || { branch: \'unknown\' }).branch, docversion: (file.src || {}).version,')
}

// populate index
class Document {
    String title
    String content
    String link
}

def readTitle = { file ->
    def firstLine = file.readLines()[0]
    if (firstLine.startsWith('=')) {
        return firstLine.substring(firstLine.lastIndexOf('=') + 1).trim()
    }
    return file.name.replace('.adoc', '').replace('-', ' ')
}
def readContent = { file ->
    file.readLines()
            .findAll { !it.startsWith(':') && !it.startsWith('=') && !it.trim().isEmpty() }
            .join(' ')
            .replace('`', '')
}
def toText = { content ->
    def html = adoc.render(content, OptionsBuilder.options()
            .safe(SafeMode.UNSAFE)
            .backend('html5')
            .headerFooter(true)
            .attributes(AttributesBuilder.attributes()
            .attribute('hide-uri-scheme')
            .attribute('idprefix')
            .attribute('idseparator')
            .attribute('partialsdir', new File(project.basedir, 'src/main/antora/modules/ROOT/pages/_partials').absolutePath)
            .attribute('imagesdir', new File(project.basedir, 'src/main/antora/modules/ROOT/assets/images').absolutePath)
            .attribute('project_version', project.properties['versions.release'])
            .attribute('docversion', project.properties['versions.release'])
            .attribute('git_branch', project.properties['git.branch'])
            .attribute('deploymentRoot', 'https://talend.github.io/component-runtime')))
    Jsoup.parse(html).body().text()
        .replaceAll("^Version: ${project.properties['versions.release']}", '')
        .replaceAll('Last updated [0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} CEST$', '')
}

def sourceSearchJs = new File(project.basedir, 'src/main/antora/modules/ROOT/pages/search.adoc')
def index = []
// browse all pages and create an index for them
new File(project.basedir, 'src/main/antora/modules/ROOT/pages').listFiles()
        .findAll {
            !it.isDirectory() && !it.name.equals('search.adoc') && !it.text.contains('include::') // we skip aggregator pages
        }
        .each { file ->
            // todo: pre tokenize?
            index.add(new Document(
                    title: readTitle(file),
                    content: toText(readContent(file)),
                    link: file.name.replace('.adoc', '.html')))
        }
// as any generated source we ensure it is deterministic
index.sort { it.link.compareTo(it.link) }

def jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true).withPropertyOrderStrategy(PropertyOrderStrategy.LEXICOGRAPHICAL))
def output = '= Search\n' +
        ':page-partial:\n' +
        ':page-talend_search: true\n\n++++\n' +
        jsonb.toJson(index) +
        '\n++++\n'
jsonb.close()
if (!sourceSearchJs.exists() || sourceSearchJs.text != output) {
    sourceSearchJs.parentFile.mkdirs()
    sourceSearchJs.text = output
    log.info("Generated search index ${sourceSearchJs}")
} else {
    log.info('search index already up to date')
}

// otherwise gh-pages ignore the _ folders
new File(project.build.directory, "${project.build.finalName}/.nojekyll").text = ''
