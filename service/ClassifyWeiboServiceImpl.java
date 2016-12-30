
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
 * ѵ����
 * 
 * 
 */
class ClassifyWeiboServiceImpl {

	// Dao
	UserDAO userDao = new UserDAOImpl();
	WeiboDAO weiboDao = new WeiboDAOImpl();
	RecommendDAO recommendDao = new RecommendDAOImpl();
	// �����Ŷ�Ӧ��ʵ������
	private Map<String, String> classMap = new HashMap<String, String>();
	// ����Ӧ��txt�ı���
	private Map<String, Integer> classP = new ConcurrentHashMap<String, Integer>();
	// �����ı���
	private AtomicInteger actCount = new AtomicInteger(0);
	// ÿ������Ӧ�Ĵʵ��Ƶ��
	private Map<String, Map<String, Double>> classWordMap = new ConcurrentHashMap<String, Map<String, Double>>();
	// ѵ������λ��
	private String trainPath = "sina";
	// �ִ�������
	private List<String> ls = new ArrayList<String>();
	private Map<String, Integer> dic = new HashMap<String, Integer>();
	private int maxwl;// ���ôʵ���󳤶�

	private static ClassifyWeiboServiceImpl train = new ClassifyWeiboServiceImpl();

	public static ClassifyWeiboServiceImpl getInstance() {
		return train;
	}

	private ClassifyWeiboServiceImpl() {
		// ��ʼ�����
		loadDic();
		realTrain();
	}

	/**
	 * ���شʵ�
	 */
	private void loadDic() {
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(
					new FileInputStream(ClassifyWeiboServiceImpl.class.getClassLoader().getResource("SogouLabDic-utf8.dic").getFile()),
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
	 * ѵ������
	 */
	private void realTrain() {
		// ��ʼ��
		classMap = new HashMap<String, String>();
		classP = new HashMap<String, Integer>();
		actCount.set(0);
		classWordMap = new HashMap<String, Map<String, Double>>();

		classMap.put("C000001", "����");
		classMap.put("C000002", "�Ļ�");
		classMap.put("C000003", "����");
		classMap.put("C000004", "ҽҩ");
		classMap.put("C000005", "����");
		classMap.put("C000006", "����");

		// �����������������
		Set<String> keySet = classMap.keySet();

		
		// ���ÿ�������ļ��ʻ�����
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

			// �洢ÿ�������ļ��ʻ�����
			List<List<String>> fileContent = new ArrayList<List<String>>();
			if (files != null) {
				for (File txt : files) {
					String content = readtxt(txt.getAbsolutePath());
					// �ִ�
					List<String> word_arr = seg_list(content);
					fileContent.add(word_arr);
					// ͳ��ÿ���ʳ��ֵĸ���
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

			// ÿ������Ӧ�Ĵʵ��Ƶ��
			classWordMap.put(classKey, wordMap);
			// ÿ������������Ŀ
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
	 * ����
	 * 
	 * @param text
	 * @return ���طֳ�������б�
	 */
	private ArrayList<String> classify(String text) {
		// �ִʣ�����ȥ��
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

				// ���������������µ�����������ľ�У���ô��������С��ֵ ��Ӱ�����
				double term_frequency = (wordCount == null) ? ((double) 1 / (articleCount + 1))
						: (wordCount / articleCount);

				// �ı������ĸ��� �����ﰴ��������������ͳ�ƣ�������=�ʻ�1/������ * �ʻ�2/������ ������
				// ��double����С��ʱ����Ϊ0��Ϊ�˱��� *10

				typeOfThis = typeOfThis * term_frequency * 10;
				typeOfThis = ((typeOfThis == 0.0) ? Double.MIN_VALUE : typeOfThis);
			}
			typeOfThis = ((typeOfThis == 1.0) ? 0.0 : typeOfThis);
			// ��������³��ֵĸ���
			double classOfAll = classP.get(classKey) / actCount.doubleValue();
			// ���ݱ�Ҷ˹��ʽ $(A|B)=S(B|A)*S(A)/S(B),����$(B)�ǳ����������ﲻ������,��Ӱ�������
			frequencyOfType.put(classKey, typeOfThis * classOfAll);
		}
		Collection<Double> c = frequencyOfType.values();
		Object[] obj = c.toArray();
		Arrays.sort(obj);
		Set<Entry<String, Double>> set = frequencyOfType.entrySet();
		// �����ص�����б�
		ArrayList<String> arr = new ArrayList<String>();
		Iterator<Entry<String, Double>> it = set.iterator();
		while (it.hasNext()) {
			// �ҵ�����key-value�Լ���
			Map.Entry entry = (Map.Entry) it.next();
			// ͨ���ж��Ƿ��и�valueֵ
			if ((Double) entry.getValue() == (Double) obj[obj.length - 1]) {
				// ȡ��keyֵ
				String s = (String) entry.getKey();
				arr.add(classMap.get(s));
			}
		}
		return arr;
	}

	public boolean readAndWriteToRedis() {
		List<String> useridList = userDao.getTotalUserId();
		List<String> weiboidList = new ArrayList<String>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		for (String string : useridList) {
			weiboidList.addAll(userDao.getWeibo(string, 1, userDao.getWeiboNumber(string)));
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
		//��ʱ���΢���б�����
		List<Map.Entry<String, Date>> weiboArray = new ArrayList<Map.Entry<String, Date>>(weiboMap.entrySet());
		Collections.sort(weiboArray, new Comparator<Map.Entry<String, Date>>() {
			public int compare(Map.Entry<String, Date> o1, Map.Entry<String, Date> o2) {
				return (o2.getValue().compareTo(o1.getValue()));
			}
		});
		//������
		List<String> labelList = new ArrayList<String>();
		for (int i = 0; i < 50; i++) {
			try {
				String weiboid = "";
				String weiboContent = "";
				weiboid = weiboArray.get(i).getKey();
				weiboContent=weiboDao.getContent(weiboid);
				labelList=classify(weiboContent);
	            recommendDao.setWeiboLabels(weiboid,labelList);
			} catch (Exception e) {
				System.out.println("û�е� " + (i + 1) + " ��΢��");
			}
		}
		return true;
	}
}
