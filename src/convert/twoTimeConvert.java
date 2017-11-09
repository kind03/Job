package convert;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;

/**<p>
 * 本段代码用以转换难以通过常规手段恢复的中文乱码，且仅针对如下情况：
 * 对GBK编码的中文，使用了不支持中文的ANSI-US(Windows-1252或ISO-8859-1) to UTF-8函数。
 * 对于其他情况的乱码，简单修改源代码后，相信也可以解决。</p><p>
 * https://github.com/kind03/Job 中有附带了测试用乱码，可以进行测试。
 * 本class中有singleWordTest()方法， 其中也附带了两个乱码字符，可以测试。</p><p>
 * 注：在纯英文的Windows系统环境下 ，可以直接使用Notepad++对此类乱码进行转码处理。</p><p>
 * 具体方法为：</p><p>
 * 一、首先确保操作系统的System Locale也设为英语： Control Pannel -- Region -- Administrative--
 * Language for non-Unicode programs也需要设置为English。</p><p>
 * 二、使用Notepad++打开包含乱码的文件，点击菜单栏中的Encoding -- Convert to ANSI，
 * 将文件转换为系统默认的ANSI-US编码，即Windows-1252（如果是中文系统，就会转换为GBK，导致转换失败），
 * 再点击Encoding -- Character sets -- Chinese -- GB2312(Simplified Chinese)，
 * 以GB2312编码解析二进制源码，就会看到熟悉的汉字！</p>
 * @author 何晶   He, Jing
 * @version 1.2 &nbsp; 2017/11/6
 *
 */
public class twoTimeConvert {
	//由于转换大文件需要分块处理，segmentSize为分块大小，默认为4096字节，可以自行改动。
	//关于文件分块的介绍请见segmentConvert()方法。
	public static final int segmentSize = 16;
	private static String inputCode = "UTF-8";
	//ISO-8859-1 or Windows-1252 are both fine
	private static String middleCode = "Windows-1252";
	private static String originCode = "GBK";
	private static String outputCode = "UTF-8";
	private static String inputPath;
	private static String outputPath;
	
	/**
	 * @param args		1 &nbsp;输入文件路径 	&nbsp; Input File Path 
	 * @param args 		2 &nbsp;输出文件路径	&nbsp; Output File Path 
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length >= 2) {
			inputPath = args[0];
			outputPath = args[1];
			} 
		if (args.length >= 3) inputCode = args[2];
		if (args.length >= 4) middleCode = args[3];
		if (args.length >= 5) originCode = args[4];
		if (args.length >= 6) outputCode = args[5];
		if (args.length > 6 || args.length<2)  {
			System.err.println("Wrong number of arguments! Got " + args.length
			+ " arguments. This script requires 2 to 6 arguments: \n"
			+ "inputFilePath, outputFilePath, "
			+ "[inputEncoding], [middleEncoding], [originEncoding] ,[outputEncoding]");
			return;
		}
		segmentConvert();
	}
	/**
	 * 	<p>由于Java的CharsetEncoder Engine每次处理的字符数量有限，String类的容量也有限，
		所以对于大文件，必须要拆分处理。但是由于UTF-8格式中每个字符的长度可变，且经过两次转换，
		原来的GBK编码已经面目全非，不太好区分每个汉字的开始和结束位置。
		所以干脆查找UTF-8中的标准ASCII的字符，即单个字节十进制值为0-127范围内的字符，
		以ASCII字符后的位置来对文件进行分块(Segementation)，再逐块转换。
		但如果在默认的分块大小(Segment Size)一个ASCII字符都找不到的话，就会导致转换失败。</p>
	 * @param inputPath		&nbsp;输入文件路径 	&nbsp; Input File Path 
	 * @param outputPath	&nbsp;输出文件路径	&nbsp; Output File Path 
	 * @throws IOException
	 */
	public static void segmentConvert() throws IOException {
		FileInputStream fis = new FileInputStream(inputPath);
		FileOutputStream fos = new FileOutputStream(outputPath);
		byte[] buffer = new byte[segmentSize];
		int len;
		int counter = 0;
		byte[] validBuffer;
		byte[] combined;
		byte[] left0 = null;
		byte[] left1 = null;
		byte[] converted;
		while((len=fis.read(buffer)) == segmentSize) {
			//to check the value of len
//			System.out.println("len = " + len);
			int i = segmentSize - 1;
			if ("UTF-16LE".equals(inputCode)) {
//				System.out.println("Input Encoding UTF-16, no need to find segment split point "
//						+ "as long as the segment size is the multiple of 2");
			}
			else if ("UTF-16BE".equals(inputCode)) {
//				System.out.println("Input Encoding UTF-16, no need to find segment split point "
//						+ "as long as the segment size is the multiple of 2");
			}else {
//				the following segmentation method is not suitable for UTF-16 or UTF-32 
//				since they are not compatible with ASCII code 
				while (buffer[i] > 127 || buffer[i]<0) {
					i--;
					if (i==0) {
						//报错
						System.err.println("File Segmentation Failed. Failed to find an "
						+ "ASCII character(0-127) in a segment size of "+
						segmentSize +" bytes\n"+"Plese adjust the segmentation size.");
						break;
					}
				}
			}
			validBuffer = Arrays.copyOf(buffer, i+1);
			if (counter%2==0){
				left0 = Arrays.copyOfRange(buffer,i+1,segmentSize);
				combined = concat(left1,validBuffer);
				left1 = null;
			} else {
				left1 = Arrays.copyOfRange(buffer,i+1,segmentSize);
				combined = concat(left0,validBuffer);
				left0 = null;
			}
			counter++;
			converted = realConvert2(combined,combined.length);
			fos.write(converted);
		}
		//for the end part of the document
		//can't use len=fis.read(buffer) since buffer has been read into in the while loop for the last time.
		if(len < segmentSize) {
			//to check the value of len
			System.out.println("len = " + len);
			if (len>0) {
				validBuffer = Arrays.copyOf(buffer, len);
			} else {
				//in case the file length is the multiple of 8
				//in this case, the length of last segment will be 0
				//only need to write what's in the left0 or left1
				validBuffer = null;
			}
			if (counter%2==0){
				//there is nothing left when dealing with the last part of the document
				//therefore no need to give value to left0 or left1
				combined = concat(left1,validBuffer);
			} else {
				combined = concat(left0,validBuffer);
			}
			converted = realConvert2(combined,combined.length);
			fos.write(converted);
//			for test purpose
			System.out.println("================= last segment check =====================");
			System.out.println(new String(converted));
		}
		fos.close();
		fis.close();
	}
	public static byte[] concat(byte[] a, byte[] b) {
		//for combining two arrays
		if (a==null) return b;
		if (b==null) return a;
		int aLen = a.length;
		int bLen = b.length;
		byte[] c= new byte[aLen+bLen];
		System.arraycopy(a, 0, c, 0, aLen);
		System.arraycopy(b, 0, c, aLen, bLen);
		return c;
	}
	
	/**
	 * 由于realConvert方法使用的CharsetEncoder Engine转换方法比较繁琐，不能直接对byte[]操作，
	 * 要先把byte[]转换为String再转换为char[]再转换为CharBuffer，而且使用CharsetEncoder转UTF-8时
	 * 还有bug，会导致结果中最后产生大量null字符，所以改用realConvert2()。
	 * realConvert2直接使用String类的构造方法String(byte[] bytes, String charsetName)
	 * 和getBytes(String charsetName)方法，更加简洁明了。
	 * @param in	&nbsp;输入字节数组
	 * @param len	&nbsp;该字节数组的有效长度。用以处理 
	 * java.io.FileInputStream.read(byte[] b)方法产生的byte[]数组中包含部分无效元素的情况。
	 * 如果in数组中所有元素都有效，该变量可直接填入in.length
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static byte[] realConvert2 (byte[] in, int len) {
		byte[] valid = Arrays.copyOf(in, len);
		try {
			String step1 = new String(valid,inputCode);
			byte[] step2 = step1.getBytes(middleCode);
			String step3 = new String(step2,originCode);
			byte[] step4 = step3.getBytes(outputCode);
			return step4;
		} catch (UnsupportedEncodingException e) {
			System.err.println("Unsupported Encoding. Please check Java 8 "
			+ "supported encodings at: http://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html");
			e.printStackTrace();
		}
		return valid;

	}
	
	public static byte[] realConvert (byte[] in, int len) throws CharacterCodingException, UnsupportedEncodingException {
		String inS = new String(Arrays.copyOf(in, len), Charset.forName("inputCode"));
		String outS = realConvert(inS);
		//不要使用"return outS.getBytes();",因为getBytes()会使用当前系统默认的字符集进行编码，
		//导致二进制码格式的不确定性。
		return outS.getBytes("outputCode");
		
		//以下代码会导致每次转换后在conv2Bytes数组最后产生大量null字符，不知道为什么。把UTF-8改成GBK就没问题。
//		CharsetEncoder encoder2 = Charset.forName("UTF-8").newEncoder();
//		ByteBuffer conv2Bytes = encoder2.encode(CharBuffer.wrap(outS.toCharArray()));
//		return conv2Bytes.array();
	}
	public static String realConvert (String inS) throws CharacterCodingException {
		CharsetEncoder encoder1 = Charset.forName(middleCode).newEncoder();
		//Ignore the converting error
		encoder1.onUnmappableCharacter(CodingErrorAction.IGNORE);
		ByteBuffer conv1Bytes = encoder1.encode(CharBuffer.wrap(inS.toCharArray()));
		return new String(conv1Bytes.array(), Charset.forName("originCode"));
	}
} 