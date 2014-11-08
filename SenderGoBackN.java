import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.CRC32;

public class Sender {
	static int pkt_size = 1000;
	static int window_size = 10;
	static int header_size = 20;
	static int timeout_interval = 500;
	static int response_size = 100;
	static ArrayList<DatagramPacket> packetQueue = new ArrayList<DatagramPacket>();
	static ArrayList<Integer> timeoutQueue = new ArrayList<Integer>();
	int sequence = 0;
	int acked = 0;
	boolean resend = false;
	boolean continueSending = true;
	Timer timer;
	TimerTask tt;
	CRC32 crc;
	boolean finished = false;

	public class OutThread extends Thread {
		private DatagramSocket sk_out;
		private int dst_port;
		private int recv_port;
		private String inputPath;
		private String outputPath;

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
				int num_bytes_read = 0, filename_byteLength = 0;
				byte[] out_data;
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
				DatagramPacket out_pkt;


				//				 To register the recv_port at the UnreliNet first
				//				DatagramPacket out_pkt = new DatagramPacket(
				//						("REG:" + recv_port).getBytes(),
				//						("REG:" + recv_port).getBytes().length, dst_addr,
				//						dst_port);
				//				sk_out.send(out_pkt);

				try {
					while (!finished) {
						if((sequence - acked) < window_size){
							continueSending = true;
						}
						//						else {
						//							System.out.println("queue full, waiting...");
						//						}
						if(resend){
							for (int i=acked;i<packetQueue.size();i++) {
								System.out.println("resending packet #" + (i));
								DatagramPacket retransmit_pkt = packetQueue.get(i);
								sk_out.send(retransmit_pkt);
							}
							resend = false;
						}
						while(continueSending && !resend && num_bytes_read != -1){
							// construct the packet
							out_data = new byte[pkt_size];

							//sequence 4
							byte[] seq = ByteBuffer.allocate(4).putInt(sequence).array();//Integer.toString(sequence).getBytes();
							System.out.println("seq: "+sequence);
							for(int i=8; i<seq.length+8; i++){
								out_data[i] = seq[i-8];
							}

							if(sequence == 0){
								byte[] fileName = outputPath.getBytes();
								filename_byteLength = fileName.length;

								//filename length 4
								byte[] filenameLength = ByteBuffer.allocate(4).putInt(filename_byteLength).array();
								System.out.println("filename_byteLength: "+filename_byteLength);
								for(int i=16; i<filenameLength.length+16; i++){
									out_data[i] = filenameLength[i-16];
								}

								//filename 100
								System.out.println("fileName: "+outputPath);
								for(int i=20; i<fileName.length+20; i++){
									out_data[i] = fileName[i-20];
								}
							}

							//file data
							num_bytes_read = -1;
							if ((num_bytes_read = fileToSend.read(out_data, sequence==0?header_size+filename_byteLength:header_size, pkt_size-(sequence==0?header_size+filename_byteLength:header_size))) == -1) {
								System.out.println("reached end of file!");
							}

							//content size 4
							byte[] num_bytes = ByteBuffer.allocate(4).putInt(num_bytes_read).array();
							System.out.println("num_bytes: "+num_bytes_read);
							for(int i=12; i<num_bytes.length+12; i++){
								out_data[i] = num_bytes[i-12];
							}

							//checksum 8
							crc = new CRC32();
							crc.update(out_data, 8, pkt_size-8);
							byte[] checksum = ByteBuffer.allocate(8).putLong(crc.getValue()).array();
							System.out.println("checksum: "+crc.getValue());
							for(int i=0; i<checksum.length; i++){
								out_data[i] = checksum[i];
							}

							// send the packet
							out_pkt = new DatagramPacket(out_data, out_data.length,
									dst_addr, dst_port);
							packetQueue.add(out_pkt);
							sk_out.send(out_pkt);

							System.out.println("sent packet #"+sequence);

							// increase counter
							sequence++;

							if((sequence - acked) >= window_size){
								continueSending = false; 
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					sk_out.close();
					fileToSend.close();
					System.exit(-1);
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
				byte[] in_data = new byte[response_size];
				DatagramPacket in_pkt = new DatagramPacket(in_data, in_data.length);
				String response_data;
				int count = 0;
				byte[] temp;
				try {
					while (true) {
						if(resend) { continue; }
						sk_in.receive(in_pkt);

						crc = new CRC32();
						crc.update(in_data, 8, response_size-8);
						temp = new byte[8];
						System.arraycopy(in_data, 0, temp, 0, 8);
						if(ByteBuffer.wrap(temp).getLong() == crc.getValue()){

							response_data = (new String(in_data,8,response_size-8)).trim();
							System.out.println("current acked: "+acked+" response received: "+response_data);

							if(response_data.startsWith("FIN")) {
								System.out.println("finished: "+response_data);
								finished = true;
								break;
							}
							else if(response_data.startsWith("ACK:") && response_data.length() > 4 && Integer.parseInt((response_data.substring(4))) == acked){
//								if(acked > 2){
//									System.out.println("removing packet from queue: "+response_data);
//								}
								System.out.println("increasing acked for "+response_data);
								acked++;
							}
							else if(response_data.startsWith("ACK:")){
								count++;
							}
							else if(response_data.startsWith("NAK")){
								System.out.println("NAK received!");
								resend = true;
							}
						}
						else{
							System.out.println("Response corrupted!");
							resend = true;
						}
						if(count > 3){
							System.out.println("3 wrong acks received, resending");
							resend = true;
							count = 0;
						}
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
			


			timer = new Timer();
			tt = new TimerTask() {
				@Override
				public void run() {
					System.out.println("timeout, resending");
					resend = true;
				}
			};
			timer.scheduleAtFixedRate(tt, timeout_interval, timeout_interval);
			

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
		new Sender(20000, 20003, "test.txt", "blahblahblahblahblahtest.txt");
		//new Sender(20000, 20003, "ALexbug2.pdf", "blahblahblahblahblahtest.pdf");
		//new Sender(20000, 20003, "galaxy.jpg", "blahblahblahblahblahtest.jpg");
	}
}
