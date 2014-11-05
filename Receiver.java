import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;

public class Receiver {
	static int pkt_size = 1000;
	static int header_size = 150;

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
			int ack = 0, seq, num_bytes = 0;
			String header_data, outputFile;
			Long crc_calculated, crc_received;
			boolean error;
			try {
				byte[] in_data = new byte[pkt_size];
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
				DatagramPacket out_pkt;
				while (true) {
					error = false;
					// receive packet
					sk2.receive(in_pkt);

					header_data = (new String(in_pkt.getData(),0,header_size));
					System.out.println(header_data);
					
					if(header_data.contains("REG:")) { continue; }

					if(!header_data.contains("C:") || !header_data.contains("S:") || !header_data.contains("O:") || !header_data.contains("N:")){
						System.err.println("Flags not found!");
						error = true;
					}
					else{
						//checksum
						try{
							crc = new CRC32();
							crc.update(in_data, header_size, pkt_size-header_size);
							crc_calculated = crc.getValue();
							crc_received = Long.parseLong((header_data.substring(2,15).trim()));
							error = crc_received != crc_calculated;
							System.out.println("checksum: "+crc_received);
						} catch (Exception e){
							e.printStackTrace();
							error = true;
						}
						
						//sequence
						try{
							seq = Integer.parseInt(header_data.substring(17,30).trim());
							if(seq-ack == 1){ ack = seq; }
							System.out.println("seq: "+seq);
						} catch (Exception e){
							e.printStackTrace();
							error = true;
						}

						//num bytes
						try{
							num_bytes = Integer.parseInt(header_data.substring(32,35).trim());
							System.out.println("num_bytes: "+num_bytes);
						} catch (Exception e){
							e.printStackTrace();
							error = true;
						}

						//output file
						try{
							outputFile = header_data.substring(50,150).trim();
							file = new File(path, outputFile);
							output = new FileOutputStream(file);
							System.out.println("outputFile: "+outputFile);
						} catch (Exception e){
							e.printStackTrace();
							error = true;
						}

					}
					
					//write response
					String response = ""; 
					if(error){ response = "NAK"; }
					else { response = "ACK:" + ack; }
					byte[] responseB = response.getBytes();
					
					// send received packet
					out_pkt = new DatagramPacket(responseB, responseB.length, dst_addr, sk3_dst_port);
					sk3.send(out_pkt);
					
					//write after sending to minimize delay
					if(!error){
						output.write(in_data, header_size, num_bytes);
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
