
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 训练器
 * 
 * 
 */
class Train {

	// Dao
	UserDAO userDao = new UserDAOImpl();
	WeiboDAO weiboDao = new WeiboDAOImpl();
	// 类别序号对应的实际名称
	private Map<String, String> classMap = new HashMap<String, String>();
	// 类别对应的txt文本数
	private Map<String, Integer> classP = new ConcurrentHashMap<String, Integer>();
	// 所有文本数
	private AtomicInteger actCount = new AtomicInteger(0);
	// 每个类别对应的词典和频数
	private Map<String, Map<String, Double>> classWordMap = new ConcurrentHashMap<String, Map<String, Double>>();
	// 训练集的位置
	private String trainPath = "\\sina";
	// 分词器属性
	private List<String> ls = new ArrayList<String>();
	private Map<String, Integer> dic = new HashMap<String, Integer>();
	private int maxwl;// 设置词的最大长度

	private static Train train = new Train();

	public static Train getInstance() {
		return train;
	}

	private Train() {
		// 初始化类别
		loadDic();
		realTrain();
	}

	/**
	 * 加载词典
	 */
	private void loadDic() {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(
					new FileInputStream(Train.class.getClassLoader().getResource("SogouLabDic-utf8.dic").getFile()),
					"utf-8"));

			String line;
			int end;
			while ((line = br.readLine()) != null) {
				end = line.indexOf('\t');
				if (end != -1)
					dic.put(line.substring(0, end), 1);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("dic load success,have been load " + dic.size() + " words!");
	}

	private boolean match(String word) {
		if (dic.containsKey(word))
			return true;
		return false;
	}

	private List<String> seg_list(String source) {
		ls.clear();
		int len;
		String word;
		maxwl = 5;
		while ((len = source.length()) > 0) {
			if (maxwl > len) {
				maxwl = len;
			}
			word = source.substring(len - maxwl);
			int start = len - maxwl;
			boolean find = false;
			while (word.length() > 1) {
				if (match(word)) {
					ls.add(word);
					find = true;
					break;
				}
				++start;
				word = source.substring(start);
			}
			if (!find) {
				ls.add(word);
			}
			source = source.substring(0, start);
		}
		Collections.reverse(ls);
		return ls;
	}

	/**
	 * 训练数据
	 */
	private void realTrain() {
		// 初始化
		classMap = new HashMap<String, String>();
		classP = new HashMap<String, Integer>();
		actCount.set(0);
		classWordMap = new HashMap<String, Map<String, Double>>();

		classMap.put("C000001", "汽车");
		classMap.put("C000002", "文化");
		classMap.put("C000003", "经济");
		classMap.put("C000004", "医药");
		classMap.put("C000005", "军事");
		classMap.put("C000006", "体育");

		// 计算各个类别的样本数
		Set<String> keySet = classMap.keySet();

		
		// 存放每个类别的文件词汇内容
		final Map<String, List<List<String>>> classContentMap = new ConcurrentHashMap<String, List<List<String>>>();
		for (String classKey : keySet) {
			Map<String, Double> wordMap = new HashMap<String, Double>();
			File f = new File(trainPath + File.separator + classKey);
			File[] files = f.listFiles(new FileFilter() {

				@Override
				public boolean accept(File pathname) {
					if (pathname.getName().endsWith(".txt")) {
						return true;
					}
					return false;
				}
			});

			// 存储每个类别的文件词汇向量
			List<List<String>> fileContent = new ArrayList<List<String>>();
			if (files != null) {
				for (File txt : files) {
					String content = readtxt(txt.getAbsolutePath());
					// 分词
					List<String> word_arr = seg_list(content);
					fileContent.add(word_arr);
					// 统计每个词出现的个数
					for (String word : word_arr) {
						if (wordMap.containsKey(word)) {
							Double wordCount = wordMap.get(word);
							wordMap.put(word, wordCount + 1);
						} else {
							wordMap.put(word, 1.0);
						}

					}
				}
			}

			// 每个类别对应的词典和频数
			classWordMap.put(classKey, wordMap);
			// 每个类别的文章数目
			classP.put(classKey, files.length);
			actCount.addAndGet(files.length);
			classContentMap.put(classKey, fileContent);
		}
	}

	private String readtxt(String path) {
		BufferedReader br = null;
		StringBuilder str = null;
		try {
			br = new BufferedReader(new FileReader(path));
			str = new StringBuilder();
			String r = br.readLine();
			while (r != null) {
				str.append(r);
				r = br.readLine();
			}
			return str.toString();
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			str = null;
			br = null;
		}
		return "";
	}

	/**
	 * 分类
	 * 
	 * @param text
	 * @return 返回分出的类别列表
	 */
	private ArrayList<String> classify(String text) {
		// 分词，并且去重
		List<String> text_words = seg_list(text);
		System.out.println(text_words);
		Map<String, Double> frequencyOfType = new HashMap<String, Double>();
		Set<String> keySet = classMap.keySet();
		for (String classKey : keySet) {
			double typeOfThis = 1.0;
			Map<String, Double> wordMap = classWordMap.get(classKey);
			for (String word : text_words) {
				Double wordCount = wordMap.get(word);
				int articleCount = classP.get(classKey);

				// 假如这个词在类别下的所有文章中木有，那么给定个极小的值 不影响计算
				double term_frequency = (wordCount == null) ? ((double) 1 / (articleCount + 1))
						: (wordCount / articleCount);

				// 文本在类别的概率 在这里按照特征向量独立统计，即概率=词汇1/文章数 * 词汇2/文章数 。。。
				// 当double无限小的时候会归为0，为了避免 *10

				typeOfThis = typeOfThis * term_frequency * 10;
				typeOfThis = ((typeOfThis == 0.0) ? Double.MIN_VALUE : typeOfThis);
			}
			typeOfThis = ((typeOfThis == 1.0) ? 0.0 : typeOfThis);
			// 此类别文章出现的概率
			double classOfAll = classP.get(classKey) / actCount.doubleValue();
			// 根据贝叶斯公式 $(A|B)=S(B|A)*S(A)/S(B),由于$(B)是常数，在这里不做计算,不影响分类结果
			frequencyOfType.put(classKey, typeOfThis * classOfAll);
		}
		Collection<Double> c = frequencyOfType.values();
		Object[] obj = c.toArray();
		Arrays.sort(obj);
		Set<Entry<String, Double>> set = frequencyOfType.entrySet();
		// 待返回的类别列表
		ArrayList<String> arr = new ArrayList<String>();
		Iterator<Entry<String, Double>> it = set.iterator();
		while (it.hasNext()) {
			// 找到所有key-value对集合
			Map.Entry entry = (Map.Entry) it.next();
			// 通过判断是否有该value值
			if ((Double) entry.getValue() == (Double) obj[obj.length - 1]) {
				// 取得key值
				String s = (String) entry.getKey();
				arr.add(classMap.get(s));
			}
		}
		return arr;
	}

	public boolean readAndWriteToRedis() {
		List<String> useridList = userDao.getUserId();
		List<String> weiboidList = new ArrayList<String>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		for (String string : useridList) {
			weiboidList.addAll(userDao.getWeibo(string, 1, Integer.parseInt(userDao.getWeiboNumber(string))));
		}
		Map<String, Date> weiboMap = new HashMap<String, Date>();
		for (String string : weiboidList) {
			try {
				weiboMap.put(string, sdf.parse(weiboDao.getsendTime(string)));
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//按时间对微博列表排序
		List<Map.Entry<String, Date>> weiboArray = new ArrayList<Map.Entry<String, Date>>(weiboMap.entrySet());
		Collections.sort(weiboArray, new Comparator<Map.Entry<String, Date>>() {
			public int compare(Map.Entry<String, Date> o1, Map.Entry<String, Date> o2) {
				return (o2.getValue().compareTo(o1.getValue()));
			}
		});
		//分类结果
		List<String> labelList = new ArrayList<String>();
		for (int i = 0; i < 50; i++) {
			try {
				String weiboid = "";
				String weiboContent = "";
				weiboid = weiboArray.get(i).getKey();
				weiboContent=weiboDao.getContent(weiboid);
				labelList=classify(weiboContent);
	            weiboDao.insertLabel(String weiboid,List<String> labelList);
			} catch (Exception e) {
				System.out.println("没有第 " + (i + 1) + " 条微博");
			}
		}
		// System.out.println(classify(""));
		return true;
	}

	public static void main(String[] args) {
		Train t = Train.getInstance();
		t.readAndWriteToRedis();
	}
}
