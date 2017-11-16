/**
 * Copyright (c) 2015 Kristian Kraljic
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package lc.kra.servlet;

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.swing.filechooser.FileSystemView;

import org.apache.commons.fileupload.ParameterParser;
import org.zeroturnaround.zip.ZipUtil;

/**
 * Servlet implementation class FileBrowserServlet
 */
@MultipartConfig
public class FileManagerServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final int BUFFER_SIZE = 4096;
	private static final String ENCODING = "UTF-8";
	private DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Files files = null; File file = null, parent;
		String path = request.getParameter("path"), type = request.getContentType(), search = request.getParameter("search"), mode;
		
		if(path==null||!(file=new File(path)).exists()) {
			try {
				files = new Roots();
			} catch (NoClassDefFoundError e) {
				e.printStackTrace();
			}
		} else if(request.getParameter("zip")!=null) {
			File zipFile = File.createTempFile(file.getName()+"-",".zip");
			if(file.isFile())
				ZipUtil.addEntry(zipFile, file.getName(), file);
			else if(file.isDirectory())
				ZipUtil.pack(file, zipFile);
			downloadFile(response, zipFile, permamentName(zipFile.getName()), "application/zip");
		} else if(request.getParameter("delete")!=null) {
			if(file.isFile()) {
				file.delete();
				files = new Directory(file.getParentFile());
			} else if(file.isDirectory()) {
				java.nio.file.Files.walkFileTree(file.toPath(),new SimpleFileVisitor<Path>() {
				   @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				   	java.nio.file.Files.delete(file);
					   return FileVisitResult.CONTINUE;
				   }
				   @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				   	java.nio.file.Files.delete(dir);
					   return FileVisitResult.CONTINUE;
				   }
			   });
			}
		} else if((mode=request.getParameter("mode"))!=null) {
			boolean add = mode.startsWith("+");
			if(mode.indexOf('r')>-1)
				file.setReadable(add);
			if(mode.indexOf('w')>-1)
				file.setWritable(add);
			if(mode.indexOf('x')>-1)
				file.setExecutable(add);
		} else if(file.isFile())
			downloadFile(response, file);
		else if(file.isDirectory()) {
			if(search!=null&&!search.isEmpty()) {
			     files = new Search(file.toPath(), search);
			} else if(type!=null&&type.startsWith("multipart/form-data")) {
				for(Part part:request.getParts()) {
					String name;
					if((name=partFileName(part))==null) //retrieves <input type="file" name="...">, no other (e.g. input) form fields
						continue;
					if(request.getParameter("unzip")==null)
						try(OutputStream output=new FileOutputStream(new File(file,name))) {
							copyStream(part.getInputStream(), output); }
					else { 
						ZipUtil.unpack(part.getInputStream(), file);
					}
				}
				files = new Directory(file);
			} else {
				files = new Directory(file);
			}
		} else throw new ServletException("Unknown type of file or folder.");
		
		if(files!=null) {
			final PrintWriter writer = response.getWriter();
			writer.println("<!DOCTYPE html><html><head><style>*,input[type=\"file\"]::-webkit-file-upload-button{font-family:monospace}</style></head><body>");
			writer.println("<p>Runtime application path: "+ getRuntimeApplicationPath() +"</p>");
			writer.println("<p>Current directory: "+files+"</p><pre>");
			if(!(files instanceof Roots)) {
				writer.print("<form method=\"post\"><label for=\"search\">Search Files:</label> <input type=\"text\" name=\"search\" id=\"search\" value=\""+(search!=null?search:"")+"\"> <button type=\"submit\">Search</button></form>");
				writer.print("<form method=\"post\" enctype=\"multipart/form-data\"><label for=\"upload\">Upload Files:</label> <button type=\"submit\">Upload</button> <button type=\"submit\" name=\"unzip\">Upload & Unzip</button> <input type=\"file\" name=\"upload[]\" id=\"upload\" multiple=\"multiple\"/></form>");
				writer.println();
			}
			if(files instanceof Directory) {
				writer.println("+ <a href=\"?path="+URLEncoder.encode(path,ENCODING)+"\">.</a>");
				if((parent=file.getParentFile())!=null)
				     writer.println("+ <a href=\"?path="+URLEncoder.encode(parent.getAbsolutePath(),ENCODING)+"\">..</a>");
				else writer.println("+ <a href=\"?path=\">..</a>");
			}

			for(File child:files.listFiles()) {
				writer.print(child.isDirectory()?"+ ":"  ");
				writer.print("<a href=\"?path="+URLEncoder.encode(child.getAbsolutePath(),ENCODING)+"\" title=\""+child.getAbsolutePath()+"\">" + child.getName() + "</a> " + dateFormat.format(child.lastModified()) );
				if(child.isDirectory()) {
					writer.print(" <a href=\"?path="+URLEncoder.encode(child.getAbsolutePath(),ENCODING)+"&zip\" title=\"download\">&#8681;</a>");
				}
				if(search!=null&&!search.isEmpty()) {
					writer.print(" <a href=\"?path="+URLEncoder.encode(child.getParentFile().getAbsolutePath(),ENCODING)+"\" title=\"go to parent folder\">&#128194;</a>");
				}
				writer.println();
			}
			writer.print("</pre></body></html>"); writer.flush();
		}
	}
	
	protected String getRuntimeApplicationPath() {
		String path = "";
		try {
			path = new File(".").getCanonicalPath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return path;
	}

	protected interface Files {
		public File[] listFiles();
	}
	protected static class Directory implements Files {
		public final File directory;

		public Directory(File directory) {
			if(!(this.directory=directory).isDirectory())
				throw new IllegalArgumentException();
		}

		@Override public String toString() { return directory.getAbsolutePath(); }
		@Override public File[] listFiles() {
			File[] files = directory.listFiles();
			Arrays.sort(files);
			return files;
		}
	}
	protected static class Roots implements Files {
		private static final FileSystemView fileSystemView = FileSystemView.getFileSystemView();

		@Override public String toString() { return "root"; }
		@Override public File[] listFiles() {
			File[] roots = File.listRoots();
			for(int root=0;root<roots.length;root++) {
				final File originalRoot = roots[root];
				roots[root] = new File(roots[root].toURI()) {
					private static final long serialVersionUID = 1l;
					@Override public String getName() {
						String displayName = fileSystemView.getSystemDisplayName(originalRoot);
						return displayName!=null&&!displayName.isEmpty()?displayName:originalRoot.getPath();
					}
				};
			} return roots;
		}
	}
	protected static class Search implements Files {
		public final Path start; public final String search;
		private PathMatcher matcher;

		public Search(Path start, String search) {
			this.start = start; matcher = FileSystems.getDefault().getPathMatcher("glob:"+(this.search=search));
		}

		@Override public String toString() { return start.toString(); }
		@Override public File[] listFiles() {
			try {
				final List<File> files = new ArrayList<>();
				java.nio.file.Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
					@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
						Path name = file.getFileName();
						if(name!=null&&matcher.matches(name))
							files.add(file.toFile());
						return CONTINUE;
					}
				}); return files.toArray(new File[0]);
			} catch (IOException e) { return null; }
		}
	}

	@SuppressWarnings("unused") private static void checkForPost(HttpServletRequest request) throws ServletException {
		if(!"POST".equals(request.getMethod()))
			throw new ServletException("method must be POST");
	}
	
	@SuppressWarnings("unused") private static byte[] readStream(InputStream input) throws IOException { return readStream(input, -1, true); }
	private static byte[] readStream(InputStream input, int length, boolean readAll) throws IOException {
		byte[] output = {}; int position = 0;
		if(length==-1) length = Integer.MAX_VALUE;
		while(position<length) {
			int bytesToRead;
			if(position>=output.length) { // Only expand when there's no room
				bytesToRead = Math.min(length - position, output.length + 1024);
				if(output.length < position + bytesToRead)
					output = Arrays.copyOf(output, position + bytesToRead);
			} else bytesToRead = output.length - position;
			int bytesRead = input.read(output, position, bytesToRead);
			if(bytesRead<0) {
				if(!readAll||length==Integer.MAX_VALUE) {
					if(output.length!=position)
						output = Arrays.copyOf(output, position);
					break;
				} else throw new EOFException("Detect premature EOF");
			}
			position += bytesRead;
		}
		return output;
	}
	private static void copyStream(InputStream input, OutputStream output) throws IOException {
		int read; byte buffer[] = new byte[BUFFER_SIZE];
		while((read=input.read(buffer))>0)
			output.write(buffer, 0, read);
	}
	
	private static void downloadFile(HttpServletResponse response, File file) throws IOException {
		downloadFile(response, file, file.getName());
	}
	private static void downloadFile(HttpServletResponse response, File file, String name) throws IOException {
		String contentType = java.nio.file.Files.probeContentType(file.toPath());
		downloadFile(response, file, name, contentType!=null?contentType:"application/octet-stream");
	}
	private static void downloadFile(HttpServletResponse response, File file, String name, String contentType) throws IOException {
		response.setContentType(contentType);
		response.setHeader("Content-Disposition", "attachment; filename=\""+name+"\"");
		copyStream(new FileInputStream(file),response.getOutputStream());
	}
	
	private static String permamentName(String temporaryName) {
		return temporaryName.replaceAll("-\\d+(?=\\.(?!.*\\.))","");
	}
	private String partFileName(Part part) {
		String header, file = null;
		if((header=part.getHeader("content-disposition"))!=null) {
			String lowerHeader = header.toLowerCase(Locale.ENGLISH);
			if(lowerHeader.startsWith("form-data")||lowerHeader.startsWith("attachment")) {
				ParameterParser parser = new ParameterParser();
				parser.setLowerCaseNames(true);
				Map<String, String> parameters = parser.parse(header, ';');
				if(parameters.containsKey("filename"))
					file = (file=parameters.get("filename"))!=null?file.trim():"";
			}
		}
		return file;
	}
}
