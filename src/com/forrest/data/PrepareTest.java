package com.forrest.data;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.forrest.data.config.ForrestDataConfig;
import com.mysql.jdbc.ServerPreparedStatement;

public class PrepareTest {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Connection con = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

		ForrestDataConfig config = new ForrestDataConfig();
		config.initConfig();
		con = config.getConnection();

		String sql = "select * from wuhp.w where id=?";
		String sql1 = "select * from wuhp.w where id=?";

		String sql3 = "SELECT @@session.tx_read_only";

		String sql2 = "insert into wuhp.w (name,age) values(?,?)";

		try {
			
			
		
			con.setAutoCommit(false);
			ps = con.prepareStatement(sql2);
			
			InputStream in = null;
			try {
				in = new FileInputStream("d:/fd_binlog_pos.info");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			// for (int i = 0; i < 3; i++) {
			
			ps.setBlob(1, in);
			ps.setInt(2, 0);
			// ps.addBatch();
			// }
			// ps.executeBatch();
			ps.executeUpdate();
			ps.close();
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			ps = con.prepareStatement(sql3);
			rs = ps.executeQuery();
			while (rs.next()) {
				System.out.println(rs.getString(1));
			}
			// ps.clearParameters();
			ps.close();
			rs.close();
			


			// ps = con.prepareStatement(sql);
			// ps.setInt(1, 126);
			// rs = ps.executeQuery();
			// while (rs.next()) {
			// System.out.println(rs.getString(1));
			// }
			// ps.clearParameters();
			// ps.close();
			// rs.close();
			//
			// ps = con.prepareStatement(sql1);
			// ps.setInt(1, 2);
			// rs = ps.executeQuery();
			// while (rs.next()) {
			// System.out.println(rs.getString(1));
			// }
		con.commit();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			config.close(con, ps, rs);
		}

	}

}
