package org.sessx.btsm;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    public static final String RUN_FEED_OUT = RUN_PATH + "/feed.xml".replace("/", File.separator);
    public static final String RUN_ND_OUT = RUN_PATH + "/ndsitemap.xml".replace("/", File.separator);

    public static void main(String[] args) throws Exception {
        // git repo
        File dir = cloneOrPull("https://github.com/SessionHu/blogtest", RUN_REPO);
        // uris
        List<File> fileList = ls(dir, RUN_REPO);
        Map<URI, File[]> fileUriMap = fileListToMap(fileList);
        // xml
        Document documentMain = createXMLDocument();
        Document documentFeed = createXMLDocument();
        createXMLSitemap(documentMain, documentFeed, fileUriMap, "https://sess.xhustudio.eu.org");
        documentToFile(documentMain, new File(RUN_OUT));
        documentToFile(documentFeed, new File(RUN_FEED_OUT));
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

    public static String readMarkdownH1(File md) {
        System.out.println("Reading Markdown H1: " + md.getName());
        try (BufferedReader in = new BufferedReader(new InputStreamReader(md.toURI().toURL().openStream()))) {
            String line = in.readLine();
            if (line.startsWith("# ")) {
                return line.substring(2);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return md.getName().replace(".md", "");
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

    public static void createXMLSitemap(Document document, Document documentFeed, Map<URI, File[]> fileUriMap, String sitePrefix)
            throws IOException {
        // rss root
        Element channel; {
            Element rss = documentFeed.createElement("rss");
            rss.setAttribute("version", "2.0");
            rss.setAttribute("xmlns:atom", "http://www.w3.org/2005/Atom");
            documentFeed.appendChild(rss);
            channel = documentFeed.createElement("channel");
            rss.appendChild(channel);
            Element title = documentFeed.createElement("title");
            title.setTextContent("SЕSSのB10GТЕ5Т");
            channel.appendChild(title);
            Element link = documentFeed.createElement("link");
            link.setTextContent(sitePrefix + "/");
            channel.appendChild(link);
            Element description = documentFeed.createElement("description");
            description.setTextContent("Session的个人博客, 这里有各种类型的有趣的文章内容, 网站基于纯前端构建.");
            channel.appendChild(description);
            Element language = documentFeed.createElement("language");
            language.setTextContent("zh-CN");
            channel.appendChild(language);
            Element copyright = documentFeed.createElement("copyright");
            int year = OffsetDateTime.now(ZoneOffset.UTC).getYear();
            copyright.setTextContent((year == 2024 ? 2024 : ("2024-" + year)) + " SessionHu");
            channel.appendChild(copyright);
            Element atomLink = documentFeed.createElement("atom:link");
            atomLink.setAttribute("href", sitePrefix + "/feed.xml");
            atomLink.setAttribute("rel", "self");
            channel.appendChild(atomLink);
        }
        // sitemap & rss main
        Element urlset = document.createElement("urlset");
        urlset.setAttribute("xmlns", "http://www.sitemaps.org/schemas/sitemap/0.9");
        for (Map.Entry<URI, File[]> entry : fileUriMap.entrySet()) {
            // url / item
            Element url = document.createElement("url");
            urlset.appendChild(url);
            Element item = documentFeed.createElement("item");
            if (entry.getKey().toString().startsWith("/#!/posts")) {
                channel.appendChild(item);
            }
            // loc / link / guid
            Element loc = document.createElement("loc");
            loc.setTextContent(sitePrefix + entry.getKey().toASCIIString());
            url.appendChild(loc);
            Element link = documentFeed.createElement("link");
            link.setTextContent(sitePrefix + entry.getKey().toASCIIString());
            item.appendChild(link);
            Element guid = documentFeed.createElement("guid");
            guid.setTextContent(sitePrefix + entry.getKey().toASCIIString().replace("#!/", ""));
            item.appendChild(guid);
            // lastmod / pubDate
            Element lastmod = document.createElement("lastmod");
            Element pubDate = documentFeed.createElement("pubDate");
            OffsetDateTime offdt0 = OffsetDateTime.parse(getFileLastModified(entry.getValue()[0], RUN_REPO));
            if (entry.getValue().length > 1) {
                OffsetDateTime offdt1 = OffsetDateTime.parse(getFileLastModified(entry.getValue()[1], RUN_REPO));
                if (offdt0.isAfter(offdt1)) {
                    lastmod.setTextContent(offdt0.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    pubDate.setTextContent(offdt0.format(DateTimeFormatter.RFC_1123_DATE_TIME));
                } else {
                    lastmod.setTextContent(offdt1.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    pubDate.setTextContent(offdt1.format(DateTimeFormatter.RFC_1123_DATE_TIME));
                }
            } else {
                lastmod.setTextContent(offdt0.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                pubDate.setTextContent(offdt0.format(DateTimeFormatter.RFC_1123_DATE_TIME));
            }
            url.appendChild(lastmod);
            item.appendChild(pubDate);
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
                // title
                Element title = documentFeed.createElement("title");
                title.setTextContent(readMarkdownH1(entry.getValue()[0]));
                item.appendChild(title);
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
            if (!relpath.startsWith("./posts/") && !relpath.endsWith(".html")) {
                // not self-created resources, skip
                continue;
            }
            // url
            Element url = document.createElement("url");
            urlset.appendChild(url);
            // loc
            Element loc = document.createElement("loc");
            if (relpath.equals("./index.html")) {
                loc.setTextContent(sitePrefix + "/");
            } else {
                loc.setTextContent(relpath.replaceFirst(".", sitePrefix));
            }
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
