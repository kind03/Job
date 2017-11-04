package convert;

import java.io.File;
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

/**
 * 本段代码用以转换难以通过常规手段恢复的中文乱码，仅针对由于错误使用
 * ANSI-US(Windows-1252或ISO-8859-1)转UTF-8函数对GBK编码的中文进行转换后产生的乱码。
 * 对于其他情况的乱码，简单修改源代码后，相信也可以解决。
 * <p>注：在纯英文的Windows系统环境下 ，可以直接使用Notepad++对此类乱码进行转码处理。</p><p>
 * 具体方法为：</p><p>
 * 一、首先确保操作系统的System Locale也设为英语： Control Pannel -- Region -- Administrative--
 * Language for non-Unicode programs也需要设置为English。</p><p>
 * 二、使用Notepad++打开包含乱码的文件，点击菜单栏中的Encoding -- Convert to ANSI，
 * 将文件转换为系统默认的ANSI-US编码，即Windows-1252（如果是中文系统，就会转换为GBK，导致转换失败），
 * 再点击Encoding -- Character sets -- Chinese -- GB2312(Simplified Chinese)，
 * 以GB2312编码解析二进制源码，就会看到熟悉的汉字！</p>
 * @author 何晶   He, Jing
 * @version 1.0 &nbsp; 2017/11/5
 *
 */
public class UTF8ToANSI {
	//由于转换大文件需要分块处理，segmentSize为分块大小，默认为4096字节，可以自行改动。
	//关于文件分块的介绍请见segmentConvert()方法。
	public static final int segmentSize = 4096;
	
	/**
	 * @param args		1 &nbsp;输入文件路径 	&nbsp; Input File Path 
	 * @param args 		2 &nbsp;输出文件路径	&nbsp; Output File Path 
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		
		//should be converted from utf-8 to Windows-1252, and then parse it as GBK
		String inputPath = args[0];
		String outputPath = args[1];
		singleWordTest();
		segmentConvert(inputPath,outputPath);
	}
	public static void singleWordTest() throws CharacterCodingException, UnsupportedEncodingException{
		String text = "Íå×ÐÂëÍ·";
		String out = Arrays.toString(text.toCharArray());
		String out2 = Arrays.toString(text.getBytes());
        System.out.println("out = " + out);
        System.out.println("out2 = " + out2);
        byte[] converted1 = realConvert2(text.getBytes(),text.length());
		System.out.println("realCovert2 = " + new String(converted1, Charset.forName("UTF-8")));
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
	public static void segmentConvert(String inputPath, String outputPath) throws IOException {

		File inFile = new File(inputPath);
		FileInputStream fis = new FileInputStream(inFile);
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
			System.out.println(new String(converted,Charset.forName("UTF-8")));
			fos.write(converted);
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
	 * 整个文件转码处理。将原文件中所有字节全部读入StringBuilder中，再进行转换。
	 * 由于String及StringBuilder类大小有限，且CharsetEncoder Engine每次能转换的字节数量更小，
	 * 转换稍大的文件时，CharsetEncoder就会自行拆分String，分块转换，导致多个字节的汉字被被懒腰斩断，造成乱码。
	 * @param inputPath		&nbsp;输入文件路径 	&nbsp; Input File Path 
	 * @param outputPath	&nbsp;输出文件路径	&nbsp; Output File Path 
	 */
	public static void wholeFileConvert(String inputPath, String outputPath){
        StringBuilder sb = new StringBuilder();
		try {
			File inFile = new File(inputPath);
			FileInputStream fis = new FileInputStream(inFile);
			FileOutputStream fos = new FileOutputStream(outputPath);
			int fileLen = (int)inFile.length();
			byte[] buffer = new byte[fileLen];
			int len;
			while((len=fis.read(buffer)) != -1) {
				sb.append(new String(Arrays.copyOf(buffer, len),Charset.forName("UTF-8")));
			}
			String converted = realConvert(sb.toString());
			fos.write(converted.getBytes());
			fos.close();
			fis.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
	public static byte[] realConvert2 (byte[] in, int len) throws UnsupportedEncodingException {
		byte[] valid = Arrays.copyOf(in, len);
		String step1 = new String(valid,"UTF-8");
		byte[] step2 = step1.getBytes("ISO-8859-1");
		String step3 = new String(step2,"GBK");
		byte[] step4 = step3.getBytes("UTF-8");
		return step4;
	}
	
	public static byte[] realConvert (byte[] in, int len) throws CharacterCodingException, UnsupportedEncodingException {
		String inS = new String(Arrays.copyOf(in, len), Charset.forName("UTF-8"));
		String outS = realConvert(inS);
		//不要使用"return outS.getBytes();",因为getBytes()会使用当前系统默认的字符集进行编码，
		//导致二进制码格式的不确定性。
		return outS.getBytes("UTF-8");
		
		//以下代码会导致每次转换后在conv2Bytes数组最后产生大量null字符，不知道为什么。把UTF-8改成GBK就没问题。
//		CharsetEncoder encoder2 = Charset.forName("UTF-8").newEncoder();
//		ByteBuffer conv2Bytes = encoder2.encode(CharBuffer.wrap(outS.toCharArray()));
//		return conv2Bytes.array();
	}
	public static String realConvert (String inS) throws CharacterCodingException {
		//ISO-8859-1 or Windows-1252 are both fine
		CharsetEncoder encoder1 = Charset.forName("ISO-8859-1").newEncoder();
		//Ignore the converting error
		encoder1.onUnmappableCharacter(CodingErrorAction.IGNORE);
		ByteBuffer conv1Bytes = encoder1.encode(CharBuffer.wrap(inS.toCharArray()));
		return new String(conv1Bytes.array(), Charset.forName("GBK"));
	}
} 
