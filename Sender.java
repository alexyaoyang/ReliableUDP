import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;

public class Sender {
	static int pkt_size = 1000;
	static int window_size = 10;
	static int header_size = 150;
	static int send_interval = 500;
	static Queue<DatagramPacket> queue = new LinkedList<DatagramPacket>();

	public class OutThread extends Thread {
		private DatagramSocket sk_out;
		private int dst_port;
		private int recv_port;
		private String inputPath;
		private String outputPath;
		private CRC32 crc;

		public OutThread(DatagramSocket sk_out, int dst_port, int recv_port, String inputPath, String outputPath) {
			this.sk_out = sk_out;
			this.dst_port = dst_port;
			this.recv_port = recv_port;
			this.inputPath = inputPath;
			this.outputPath = outputPath;
		}

		public void run() {
			try {
				FileInputStream fileToSend = new FileInputStream(inputPath);
				int sequence = 1, num_bytes_read;
				byte[] out_data;
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");

//				 To register the recv_port at the UnreliNet first
				DatagramPacket out_pkt = new DatagramPacket(
						("REG:" + recv_port).getBytes(),
						("REG:" + recv_port).getBytes().length, dst_addr,
						dst_port);
				sk_out.send(out_pkt);

				try {
					while (true) {
						// construct the packet
						out_data = new byte[pkt_size];
						
						//file data
						num_bytes_read = -1;
						if ((num_bytes_read = fileToSend.read(out_data, header_size, pkt_size-header_size)) == -1) {
							System.out.println("reached end of file!");
							
							System.exit(-1);
						}
						
						//checksum 12
						crc = new CRC32();
						crc.update(out_data, header_size, pkt_size-header_size);
						byte[] checksum = ("C:"+Long.toString(crc.getValue())).getBytes();
						System.out.println(checksum.length);
						for(int i=0; i<checksum.length; i++){
							out_data[i] = checksum[i];
						}
						
						//sequence 12
						byte[] seq = ("S:"+Integer.toString(sequence)).getBytes();
						System.out.println(seq.length);
						for(int i=15; i<seq.length+15; i++){
							out_data[i] = seq[i-15];
						}
						
						//content size 5
						byte[] num_bytes = ("N:"+Integer.toString(num_bytes_read)).getBytes();
						System.out.println(num_bytes.length);
						for(int i=30; i<num_bytes.length+30; i++){
							out_data[i] = num_bytes[i-30];
						}
						
						//filename 100
						byte[] fileName = ("O:"+outputPath).getBytes();
						System.out.println(fileName.length);
						for(int i=48; i<fileName.length+48; i++){
							out_data[i] = fileName[i-48];
						}
						
						// send the packet
						out_pkt = new DatagramPacket(out_data, out_data.length,
								dst_addr, dst_port);
						queue.offer(out_pkt);
						sk_out.send(out_pkt);
						
						System.out.println("sent packet #"+sequence);

						// wait for a while
						sleep(send_interval);

						// increase counter
						sequence++;
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					sk_out.close();
					fileToSend.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public class InThread extends Thread {
		private DatagramSocket sk_in;

		public InThread(DatagramSocket sk_in) {
			this.sk_in = sk_in;
		}

		public void run() {
			try {
				byte[] in_data = new byte[pkt_size];
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);
				try {
					while (true) {
						sk_in.receive(in_pkt);
						System.out.print((new Date().getTime())
								+ ": sender received " + in_pkt.getLength()
								+ "bytes from "
								+ in_pkt.getAddress().toString() + ":"
								+ in_pkt.getPort() + ". data are ");
						for (int i = 0; i < pkt_size; ++i)
							System.out.print(in_data[i]);
						System.out.println();
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					sk_in.close();
				}
			} catch (Exception e) {	
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public Sender(int sk1_dst_port, int sk4_dst_port, String inputPath, String outputPath) {
		DatagramSocket sk1, sk4;
		System.out.println("sk1_dst_port=" + sk1_dst_port + ", "
				+ "sk4_dst_port=" + sk4_dst_port + ".");

		try {
			// create sockets
			sk1 = new DatagramSocket();
			sk4 = new DatagramSocket(sk4_dst_port);

			// create threads to process data
			InThread th_in = new InThread(sk4);
			OutThread th_out = new OutThread(sk1, sk1_dst_port, sk4_dst_port, inputPath, outputPath);
			th_in.start();
			th_out.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void main(String[] args) {
		// parse parameters
//		if (args.length != 4) {
//			System.err
//					.println("Usage: java TestSender sk1_dst_port, sk4_dst_port, inputFilePath, outputFileName");
//			System.exit(-1);
//		} else
//			new Sender(Integer.parseInt(args[0]), Integer.parseInt(args[1]), args[2], args[3]);
		new Sender(20000, 20003, "mytext.txt", "blahblahblahblahblahtest.txt");
	}
}
