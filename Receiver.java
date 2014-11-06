import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.zip.CRC32;

public class Receiver {
	static int pkt_size = 1000;
	static int header_size = 20;

	public Receiver(int sk2_dst_port, int sk3_dst_port, String outputPath) {
		DatagramSocket sk2, sk3;
		System.out.println("sk2_dst_port=" + sk2_dst_port + ", "
				+ "sk3_dst_port=" + sk3_dst_port + ".");

		// create sockets
		try {
			sk2 = new DatagramSocket(sk2_dst_port);
			sk3 = new DatagramSocket();
			File path = new File(outputPath);
			path.mkdirs();
			File file = null;
			FileOutputStream output = null;
			CRC32 crc;
			int ack = 0, seq = -1, num_bytes = 0, filename_length = 0;
			String outputFile, response;
			boolean error;
			try {
				byte[] in_data = new byte[pkt_size];
				DatagramPacket in_pkt = new DatagramPacket(in_data, in_data.length);
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
				DatagramPacket out_pkt;
				byte[] temp;
				while (true) {
					error = false;
					response = "";
					// receive packet
					sk2.receive(in_pkt);

					//checksum first
					crc = new CRC32();
					crc.update(in_data, 8, pkt_size-8);
					temp = new byte[8];
					System.arraycopy(in_data, 0, temp, 0, 8);
					if(ByteBuffer.wrap(temp).getLong() == crc.getValue()){

						//sequence
						try{
							temp = new byte[4];
							System.arraycopy(in_data, 8, temp, 0, 4);
							seq = ByteBuffer.wrap(temp).getInt();
							if(seq-ack == 1 || seq == 0){ ack = seq; }
							System.out.println("seq: "+seq);
						} catch (Exception e){
							e.printStackTrace();
							error = true;
						}

						//num bytes
						try{
							temp = new byte[4];
							System.arraycopy(in_data, 12, temp, 0, 4);
							num_bytes = ByteBuffer.wrap(temp).getInt();		
							System.out.println("num_bytes: "+num_bytes);
							if(num_bytes == -1) { response = "FIN"; }
						} catch (Exception e){
							e.printStackTrace();
							error = true;
						}

						//output file
						if (seq == 0 || file == null) {
							//file name length
							try{
								temp = new byte[4];
								System.arraycopy(in_data, 16, temp, 0, 4);
								filename_length = ByteBuffer.wrap(temp).getInt();
								System.out.println("filename_length: "+filename_length);
								try{
									outputFile = new String(in_data,20,filename_length).trim();
									file = new File(path, outputFile);
									output = new FileOutputStream(file);
									System.out.println("outputFile: "+outputFile);
								} catch (Exception e){
									e.printStackTrace();
									error = true;
								}
							} catch (Exception e){
								e.printStackTrace();
								error = true;
							}
						}

					}
					else { System.err.println("corrupted/reordered packet"); error = true; }

					//write response
					if(response.isEmpty()){
						if(error){ response = "NAK"; }
						else { response = "ACK:" + ack; }
					}
					byte[] responseB = response.getBytes();

					// send received packet
					out_pkt = new DatagramPacket(responseB, responseB.length, dst_addr, sk3_dst_port);
					sk3.send(out_pkt);

					//exit if finished
					if(response.compareTo("FIN")==0){ output.flush();System.exit(-1); }

					//write after sending to minimize delay
					if(!error){
						output.write(in_data, seq==0?header_size+filename_length:header_size, num_bytes);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				try {
					output.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				System.exit(-1);
			} finally {
				sk2.close();
				sk3.close();
				try {
					output.close();
				} catch (IOException e2) {
					e2.printStackTrace();
				}
			}
		} catch (SocketException e1) {	
			e1.printStackTrace();
		}
	}

	public static void main(String[] args) {
		// parse parameters
		//		if (args.length != 2) {
		//			System.err.println("Usage: java TestReceiver sk2_dst_port, sk3_dst_port, outputPath");
		//			System.exit(-1);
		//		} else
		//			new Receiver(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2]);
		new Receiver(20001, 20002, "./test/");
	}
}
