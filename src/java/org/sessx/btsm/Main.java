package org.sessx.btsm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Main {

    public static final String RUN_PATH = "./run".replace("/", File.separator);
    public static final String RUN_REPO = RUN_PATH + "/repo".replace("/", File.separator);
    public static final String RUN_ND_REPO = RUN_PATH + "/ndrepo".replace("/", File.separator);
    public static final String RUN_OUT = RUN_PATH + "/sitemap.xml".replace("/", File.separator);
    public static final String RUN_ND_OUT = RUN_PATH + "/ndsitemap.xml".replace("/", File.separator);

    public static void main(String[] args) throws Exception {
        // git repo
        File dir = cloneOrPull("https://github.com/SessionHu/blogtest", RUN_REPO);
        // uris
        List<File> fileList = ls(dir, RUN_REPO);
        Map<URI, File[]> fileUriMap = fileListToMap(fileList);
        // xml
        Document documentMain = createXMLDocument();
        createXMLSitemap(documentMain, fileUriMap, "https://sess.xhustudio.eu.org");
        documentToFile(documentMain, new File(RUN_OUT));
        Document documentNetdisk = createXMLDocument();
        createNetdiskXMLSitemapDocument(documentNetdisk, "https://netdisk.xhustudio.eu.org");
        documentToFile(documentNetdisk, new File(RUN_ND_OUT));
        // exit
        System.out.println("Done!");
    }

    // #region shell

    public static List<File> ls(File dir, String repoPath) throws IOException {
        List<File> fileList = new ArrayList<>();
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                if (f.getName().startsWith(".git")) {
                    continue;
                } else {
                    fileList.addAll(ls(f, repoPath));
                    continue;
                }
            }
            String fname = f.getPath().replaceFirst(repoPath, "");
            System.out.printf("- %6d %s\n", f.length(), fname);
            fileList.add(f);
        }
        return fileList;
    }

    public static void rm(File f) {
        if (f.isDirectory()) {
            for (File s : f.listFiles()) {
                rm(s);
            }
        }
        f.delete();
    }

    // #region git

    public static File cloneOrPull(String url, String path) throws IOException {
        File file = new File(path);
        String[] cmd;
        if (!file.exists()) {
            cmd = new String[] {
                    "git", "clone", url, file.getPath()
            };
        } else {
            cmd = new String[] {
                    "sh", "-c", "cd " + file.getPath() + " && git fetch origin && git reset --hard FETCH_HEAD"
            };
        }
        System.out.println("Running: " + Arrays.toString(cmd));
        Process proc = new ProcessBuilder(cmd).start();
        printProcStd(proc);
        return file;
    }

    public static String getFileLastModified(File f, String repoPath) throws IOException {
        String relpath = f.getPath().replaceFirst(repoPath, ".");
        String[] cmd = {
                "git", "--no-pager", "log", "--pretty=format:%aI", "--max-count=1", "--", relpath
        };
        System.out.println("Running: " + Arrays.toString(cmd));
        Process proc = new ProcessBuilder(cmd).directory(new File(repoPath)).start();
        return procStdoutToString(proc);
    }

    // #endregion
    // #region process

    public static void printProcStd(Process process) throws IOException {
        InputStream in = process.getInputStream();
        InputStream err = process.getErrorStream();
        while (true) {
            if (err.available() > 0) {
                System.out.write(err.read());
            } else if (in.available() > 0) {
                System.out.write(in.read());
            } else if (!process.isAlive()) {
                System.out.println("Process exit with code " + process.exitValue());
                break;
            }
        }
    }

    public static String procStdoutToString(Process process) throws IOException {
        while (process.isAlive()) {
            // running, do nothing...
        }
        if (process.exitValue() != 0) {
            System.out.println("Process exit with code " + process.exitValue());
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int bufsize;
        InputStream in = process.getInputStream();
        while ((bufsize = in.read(buf, 0, 1024)) > -1) {
            baos.write(buf, 0, bufsize);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    // #endregion
    // #region filter

    public static Map<URI, File[]> fileListToMap(List<File> fileList) {
        Map<URI, File[]> fileUriMap = new HashMap<>();
        for (File file : fileList) {
            String path = file.getPath().replaceFirst(RUN_REPO, "");
            // invaild page to user
            if (path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".json") ||
                    path.startsWith("/.")) {
                continue;
            }
            // replace
            if (path.endsWith(".md")) {
                // do nothing
            } else if (path.equals("/home.html")) {
                // same as index.html
                continue;
            } else if (path.equals("/index.html")) {
                // index.html
                path = "/";
            } else if (path.endsWith(".html")) {
                // *.html
                path = path.replace(".html", "");
            }
            // files
            File[] files;
            if (path.equals("/") || path.equals("/home") || path.equals("/category")) {
                files = new File[] { file, new File(RUN_REPO + "/posts/index.json") };
            } else {
                files = new File[] { file };
            }
            // add "/#!"
            if (!path.equals("/")) {
                path = "/#!" + path;
            }
            // uri
            try {
                fileUriMap.put(new URI(path), files);
            } catch (java.net.URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return fileUriMap;
    }

    // #endregion
    // #region xml

    public static Document createXMLDocument() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    public static File documentToFile(Document document, File outputFile) throws Exception {
        DOMSource src = new DOMSource(document);
        StreamResult result = new StreamResult(outputFile);
        TransformerFactory.newInstance().newTransformer().transform(src, result);
        return outputFile;
    }

    public static void createXMLSitemap(Document document, Map<URI, File[]> fileUriMap, String sitePrefix)
            throws IOException {
        Element urlset = document.createElement("urlset");
        urlset.setAttribute("xmlns", "http://www.sitemaps.org/schemas/sitemap/0.9");
        for (Map.Entry<URI, File[]> entry : fileUriMap.entrySet()) {
            // url
            Element url = document.createElement("url");
            urlset.appendChild(url);
            // loc
            Element loc = document.createElement("loc");
            loc.setTextContent(sitePrefix + entry.getKey().toASCIIString());
            url.appendChild(loc);
            // lastmod
            Element lastmod = document.createElement("lastmod");
            if (entry.getValue().length > 1) {
                OffsetDateTime offdt0 = OffsetDateTime.parse(getFileLastModified(entry.getValue()[0], RUN_REPO));
                OffsetDateTime offdt1 = OffsetDateTime.parse(getFileLastModified(entry.getValue()[1], RUN_REPO));
                if (offdt0.isAfter(offdt1)) {
                    lastmod.setTextContent(offdt0.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                } else {
                    lastmod.setTextContent(offdt1.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
            } else {
                lastmod.setTextContent(getFileLastModified(entry.getValue()[0], RUN_REPO));
            }
            url.appendChild(lastmod);
            // changefreq & priority
            Element changefreq = document.createElement("changefreq");
            Element priority = document.createElement("priority");
            if (entry.getKey().toString().startsWith("/#!/posts")) {
                // posts
                if (entry.getKey().toString().contains(String.valueOf(OffsetDateTime.now().getYear()))) {
                    changefreq.setTextContent("monthly");
                } else if (entry.getKey().toString().contains(String.valueOf(OffsetDateTime.now().getYear() - 1))) {
                    changefreq.setTextContent("yearly");
                } else {
                    changefreq.setTextContent("never");
                }
                priority.setTextContent("0.9");
            } else if (entry.getKey().toString().startsWith("/#!/category")) {
                // category
                changefreq.setTextContent("weekly");
                priority.setTextContent("0.7");
            } else if (entry.getKey().toString().startsWith("/#!/about")) {
                // about
                changefreq.setTextContent("monthly");
                priority.setTextContent("0.8");
            } else if (entry.getKey().toString().equals("/#!/")) {
                // home
                changefreq.setTextContent("weekly");
                priority.setTextContent("1.0");
            } else {
                changefreq.setTextContent("weekly");
                priority.setTextContent("0.5");
            }
            url.appendChild(changefreq);
            url.appendChild(priority);
        }
        document.appendChild(urlset);
    }

    public static void createNetdiskXMLSitemapDocument(Document document, String sitePrefix) throws IOException {
        // urlset
        Element urlset = document.createElement("urlset");
        urlset.setAttribute("xmlns", "http://www.sitemaps.org/schemas/sitemap/0.9");
        document.appendChild(urlset);
        // clone & ls
        File dir = cloneOrPull("https://github.com/SessionHu/gh-netdisk", RUN_ND_REPO);
        List<File> fileList = ls(dir, RUN_ND_REPO);
        // add for each
        for (File file : fileList) {
            String relpath = file.getPath().replaceFirst(RUN_ND_REPO, ".");
            if (!relpath.startsWith("./posts/")) {
                // not self-created resources, skip
                continue;
            }
            // url
            Element url = document.createElement("url");
            urlset.appendChild(url);
            // loc
            Element loc = document.createElement("loc");
            loc.setTextContent(relpath.replaceFirst(".", sitePrefix));
            url.appendChild(loc);
            // lastmod
            Element lastmod = document.createElement("lastmod");
            lastmod.setTextContent(getFileLastModified(file, RUN_ND_REPO));
            url.appendChild(lastmod);
            // changefreq
            Element changefreq = document.createElement("changefreq");
            changefreq.setTextContent("never");
            url.appendChild(changefreq);
        }
    }

    // #endregion

}
