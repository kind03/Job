package convert;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**<p>
 * 本段代码用于恢复中文乱码，主要针对被错误转换后导致无法通过直接选择文件内码进行恢复的乱码。
 * 比如一段GBK编码的文本，某程序错误使用了不支持中文的Windows-1252 to UTF-8函数进行转换，
 * 导致所有中文全部变成了带音调符号的拉丁字母，比如Æ·Ãû。这时候可以把乱码从UTF-8转换回Windows-1252，
 * 再使用GBK解析，得到中文。</p><p>
 * 本程序可以使用2-6个参数：
 * inputFilePath, outputFilePath, [inputEncoding], [middleEncoding], [originEncoding], [outputEncoding]
 * </p><p>参数使用空格分隔。其中前两个参数必填，后4个参数可选。</p><p>
 * inputFilePath：需转换的乱码文件的路径。</p><p>
 * outputFilePath：转换后文件的路径。如该路径指向的文件已存在，将覆盖。</p><p>
 * inputEncoding：乱码文件目前的编码方式。以前文的例子为例，该参数应填写UTF-8。默认值为UTF-8。</p><p>
 * middleEncoding：首次转换需要转至的编码。以前文的例子为例，该参数应填写Windows-1252。默认值为Windows-1252。</p><p>
 * originEncoding：乱码文件最原始的编码。以前文的例子为例，该参数应填写GBK。默认值为GBK。</p><p>
 * outputEncoding：最后输出文件的编码。以前文的例子为例，该参数可填写：GBK、UTF-8、UTF-16等支持中文字符的编码。默认值为UTF-8。</p><p>
 * 该程序所支持的编码为所有Java所支持的编码类型，请参考：http://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html</p><p>
 * 我在GitHub上提供了测试用乱码文件，可以进行测试。https://github.com/kind03/Job/blob/master/test_resources/MessyCodeGBK-Windows1252-UTF.txt</p>
 * @author 何晶   He, Jing
 * @version 1.3 &nbsp; 2017/11/9
 *
 */
public class twoTimeConvert {
	//由于转换大文件需要分块处理，segmentSize为分块大小，默认为4096字节，可以自行改动。
	//关于文件分块的介绍请见segmentConvert()方法。
	public static final int segmentSize = 4096;
	private static String inputCode = "UTF-8";
	//ISO-8859-1 or Windows-1252 are both fine
	private static String middleCode = "Windows-1252";
	private static String originCode = "GBK";
	private static String outputCode = "UTF-8";
	private static String inputPath;
	private static String outputPath;
	
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
			+ "[inputEncoding], [middleEncoding], [originEncoding] ,[outputEncoding]."
			+ "Arguments should be divided by spaces.");
			return;
		}
		segmentConvert();
	}
	/**
	 * 	<p>由于Java的CharsetEncoder Engine每次处理的字符数量有限，String类的容量也有限，
		所以对于大文件，必须要拆分处理。</p><p>
		但是由于UTF-8格式中每个字符的长度可变，且经过两次转换，
		原来的GBK编码已经面目全非，不太好区分每个汉字的开始和结束位置。
		所以干脆查找UTF-8中的标准ASCII的字符，即单个字节十进制值为0-127范围内的字符，
		以ASCII字符后的位置来对文件进行分块(Segementation)，再逐块转换。
		但如果在默认的分块大小(Segment Size)一个ASCII字符都找不到的话，就会导致转换失败。</p><p>
		UTF-16也按照此原理进行转换。但由于UTF-16有大端(BE)和小端(LE)之分，
		文件头部有时还有BOM，所以增加了BOM信息读取并通过BOM来判断是BE还是LE。</p><p>
		对于其他编码，只要和ASCII码兼容，都适用于对UTF-8进行分割的方法。</p>
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
		//文件头部BOM信息读取
		if ("UTF-16".equals(inputCode) || "UTF-16LE".equals(inputCode) ||"UTF-16BE".equals(inputCode)) {
			byte[] head = new byte[2];
			fis.read(head,0,2);
			if (head[0]==-1 && head[1]==-2) {
				inputCode = "UTF-16LE";
				}
			else if (head[0]==-2 && head[1]==-1) { 
				inputCode = "UTF-16BE";
				}
			else {
				left0 = head;
				counter++;
			}
		}
		while((len=fis.read(buffer)) == segmentSize) {
			//to check the value of len
//			System.out.println("len = " + len);
			int i = segmentSize - 1;
			if ("UTF-16LE".equals(inputCode)) {
				while (i>-1) {
					if ((buffer[i-1] >= 0 && buffer[i-1] <= 127) && buffer[i] ==0) {
						break;}
					i--;
					if (i==0) {
						//报错
						System.err.println("File Segmentation Failed. Failed to find an "
						+ "ASCII character(0x0000-0x0009) in a segment size of "+
						segmentSize +" bytes\n"+"Plese adjust the segmentation size.");
						break;
					}
				}
//				i = segmentSpliter(buffer,"(buffer[i-1] >= 0 || buffer[i-1] <= 127) "
//						+ "&& (buffer[i]==0)");
			}else if ("UTF-16BE".equals(inputCode)) {
				while (i>-1) {
					if ((buffer[i] >= 0 && buffer[i] <= 127) && buffer[i-1] ==0) {
						break;}
					i--;
					if (i==0) {
						//报错
						System.err.println("File Segmentation Failed. Failed to find an "
						+ "ASCII character(0x0000-0x0009) in a segment size of "+
						segmentSize +" bytes\n"+"Plese adjust the segmentation size.");
						break;
					}
				}
			}else {
//				the following segmentation method is not suitable for UTF-16 or UTF-32 
//				since they are not compatible with ASCII code 
				while (i>-1) {
					if (buffer[i] <= 127 && buffer[i] >= 0) {
						break;}
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
		//can't use len=fis.read(buffer) since buffer has already got the bytes 
		//of the last part in the while loop above
		if(len < segmentSize) {
			//to check the value of len
			System.out.println("last part len = " + len);
			if (len>0) {
				validBuffer = Arrays.copyOf(buffer, len);
			} else {
				//in case the file length is the multiple of segmentSize
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
}