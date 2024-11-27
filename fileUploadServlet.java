package cs.edu;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Scanner;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import org.apache.commons.text.StringEscapeUtils;
import java.io.File;


@WebServlet("/fileUploadServlet")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 10, // 10 MB
        maxFileSize = 1024 * 1024 * 10,      // 10 MB
        maxRequestSize = 1024 * 1024 * 10   // 10 MB
)
public class fileUploadServlet extends HttpServlet {

    private static final long serialVersionUID = 205242440643911308L;

    private static final String UPLOAD_DIR = "uploaded_files";
    private static final String CONFIG_FILE = "C:\\properties\\db.properties"; // Set the path

    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    @Override
    public void init() throws ServletException {
        super.init();
        loadDatabaseConfig();
    }

    private void loadDatabaseConfig() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
            dbUrl = props.getProperty("db.url");
            dbUser = props.getProperty("db.user");
            dbPassword = props.getProperty("db.password");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load database configuration", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String applicationPath = request.getServletContext().getRealPath("");
            String uploadFilePath = applicationPath + File.separator + UPLOAD_DIR;
            File fileSaveDir = new File(uploadFilePath);
            if (!fileSaveDir.exists()) {
                fileSaveDir.mkdirs();
            }

            String fileName = "";

            // Save uploaded file
            for (Part part : request.getParts()) {
                fileName = getFileName(part);
                fileName = fileName.substring(fileName.lastIndexOf("\\") + 1);

                if (!isAllowedFileExtension(fileName)) {
                    response.getWriter().println("Only text-based files are allowed (e.g., .txt, .csv, .json, .xml).");
                    return;
                }

                part.write(uploadFilePath + File.separator + fileName);
            }

            String content;
            try (Scanner scanner = new Scanner(new File(uploadFilePath + File.separator + fileName))) {
                content = scanner.useDelimiter("\\Z").next();
            } catch (IOException e) {
                content = "Error reading file content: " + e.getMessage();
                e.printStackTrace();
            }

            response.getWriter().write("Content in the file: " + content + "\n");

            insertFileRecord(fileName, content, response);

        } catch (IllegalStateException e) {
            response.getWriter().println("File is too large. Maximum allowed file size is 10 MB.");
            e.printStackTrace();
        }
    }

    private String getFileName(Part part) {
        String contentDisp = part.getHeader("content-disposition");
        String[] tokens = contentDisp.split(";");
        for (String token : tokens) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf("=") + 2, token.length() - 1);
            }
        }
        return "";
    }

    private boolean isAllowedFileExtension(String fileName) {
        String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return fileExtension.equals("txt") || fileExtension.equals("csv") || fileExtension.equals("json")
                || fileExtension.equals("xml");
    }

    private void insertFileRecord(String fileName, String fileContent, HttpServletResponse response) throws IOException {
        String insertSQL = "INSERT INTO uploaded_files (file_name, file_content) VALUES (?, ?)";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                 PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {

                pstmt.setString(1, fileName);
                pstmt.setString(2, fileContent);

                int rowsInserted = pstmt.executeUpdate();
                response.getWriter().println("File details successfully inserted into the database.");

            }
        } catch (SQLException e) {
            response.getWriter().println("Database error: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            response.getWriter().println("JDBC Driver not found: " + e.getMessage());
        }
    }
}
