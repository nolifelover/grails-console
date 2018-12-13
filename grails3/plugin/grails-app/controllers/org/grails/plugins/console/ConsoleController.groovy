package org.grails.plugins.console

import grails.converters.JSON
import grails.transaction.Transactional
import org.apache.commons.io.FilenameUtils

@Transactional
class ConsoleController {

    def consoleService
    def consoleConfig

    def index() {
        Map model = [
            json: [
                implicitVars: [
                    config:             'the Grails configuration',
                    console:            'the browser console',
                    ctx:                'the Spring application context',
                    grailsApplication:  'the Grails application',
                    out:                'the output PrintStream',
                    request:            'the HTTP request',
                    session:            'the HTTP session',
                ],
                baseUrl: getBaseUrl(),
                remoteFileStoreEnabled: consoleConfig.remoteFileStoreEnabled,
                groovyVersion: GroovySystem.version,
                grailsVersion: grailsApplication.metadata['app.grails.version']
            ]
        ]

        if (consoleConfig.newFileText != null) {
            model.json.newFileText = consoleConfig.newFileText
        }

        model.json.indentWithTabs = consoleConfig.indentWithTabs
        model.json.tabSize = consoleConfig.tabSize
        model.json.indentUnit = consoleConfig.indentUnit

        if (consoleConfig.remoteFileStoreDefaultPath != null) {
            model.json.remoteFileStoreDefaultPath = consoleConfig.remoteFileStoreDefaultPath
        }

        if (consoleConfig.csrfProtectionEnabled) {
            if (!session['CONSOLE_CSRF_TOKEN']) {
                session['CONSOLE_CSRF_TOKEN'] = UUID.randomUUID().toString()
            }
            model.json.csrfToken = session['CONSOLE_CSRF_TOKEN']
        }

        render view: 'index', model: model
    }

    def execute(String code, boolean autoImportDomains) {
        Evaluation eval = consoleService.eval(code, autoImportDomains, request)

        JSON.use('console') {
            render eval as JSON
        }
    }

    def listFiles(String path) {
        if (!consoleConfig.remoteFileStoreEnabled) {
            return renderError("Remote file store disabled", 403)
        }

        File baseDir = new File(path)

        if (!baseDir.exists() || !baseDir.canRead()) {
            return renderError("Directory not found or cannot be read: $path", 400)
        }
        Map result = [
            path: FilenameUtils.normalize(baseDir.absolutePath, true),
            files: baseDir.listFiles().sort { it.name }.collect { fileToJson it, false }
        ]
        render result as JSON
    }

    def file() {
        if (!consoleConfig.remoteFileStoreEnabled) {
            return renderError("Remote file store disabled", 403)
        }
        switch (request.method) {
            case 'GET':
                doFileGet()
                break
            case 'DELETE':
                doFileDelete()
                break
            case 'PUT':
                doFilePut()
                break
            case 'POST':
                doFilePost()
                break
        }
    }

    private doFileGet() {
        String filename = params.path

        if (!filename) {
            return renderError('param required: path', 400)
        }

        File file = new File(filename)

        if (!file.exists() || !file.canRead()) {
            return renderError("File $filename doesn't exist or cannot be read", 400)
        }

        render(fileToJson(file) as JSON)
    }

    private doFileDelete() {
        String filename = params.path

        if (!filename) {
            return renderError('param required: path', 400)
        }

        File file = new File(filename)

        if (!file.exists() || !file.canWrite()) {
            return renderError("File $filename doesn't exist or cannot be deleted", 400)
        }

        if (!file.delete()) {
            return renderError("File $filename could not be deleted", 400)
        }

        render([:] as JSON)
    }

    private doFilePut() {
        String filename = params.path

        if (!filename) {
            return renderError('param required: path', 400)
        }

        File file = new File(filename)

        if (!file.exists() || !file.canWrite()) {
            return renderError("File $filename doesn't exist or cannot be modified", 400)
        }

        def json = request.JSON
        try {
            file.write json.text
        } catch (e) {
            return renderError("File $filename could not be modified", 400)
        }

        render(fileToJson(file) as JSON)
    }

    private doFilePost() {
        def json = request.JSON

        File file = new File(json.path.toString(), json.name.toString())
        try {
            file.write json.text
        } catch (e) {
            return renderError("File $json.name could not be created", 500)
        }

        render(fileToJson(file) as JSON)
    }

    private def renderError(String error, int status) {
        response.status = status
        render([error: error] as JSON)
    }

    private String getBaseUrl() {
        def baseUrl = consoleConfig.baseUrl
        if (baseUrl instanceof List) {
            String serverName = request.serverName
            baseUrl = baseUrl.find { String baseUrlFromConfig ->
                baseUrlFromConfig.contains(serverName)
            }
        }
        if (baseUrl == null) {
            baseUrl = createLink(action: 'index', absolute: true) - '/index'
        }
        baseUrl
    }

    private static Map fileToJson(File file, boolean includeText = true) {
        Map json = [
            id: FilenameUtils.normalize(file.absolutePath, true),
            name: file.name,
            type: file.isDirectory() ? 'dir' : 'file',
            lastModified: file.lastModified()
        ]
        if (includeText && file.isFile()) {
            json.text = file.text
        }
        json
    }
}
