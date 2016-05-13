package de.dfki.lt.algorithm;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.dfki.lt.data.Sentence;
import de.dfki.lt.features.Alphabet;
import de.dfki.lt.features.CovingtonFeatureModel;
import de.dfki.lt.features.Feature;
import de.dfki.lt.features.FeatureModel;
import de.dfki.lt.features.FeatureVector;
import de.dfki.lt.pil.MorphFeatureVector;

public class CovingtonAlgorithm extends ParsingAlgorithm{

	public void setNumberOfConfigurations(int numberOfConfigurations) {
		super.setNumberOfConfigurations(numberOfConfigurations);
	}

	public int getNumberOfConfigurations() {
		return super.getNumberOfConfigurations();
	}
	
	private void postprocess(String[][] sentArray, Sentence sent, DependencyStructure depStruct) {
		Set<Integer> headless = new HashSet<Integer>();
		for (int j=0; j < sentArray.length; j++) {
			if (sentArray[j][6] == null || sentArray[j][6].equals("_") || sentArray[j][6].equals("-1")) {
				headless.add(j);
			}
		}	
	//	System.out.println(sent.getRootPosition()+" "+headless);
		if (!headless.isEmpty()) {
			Integer rootPosition = sent.getRootPosition();
			if (rootPosition == null || rootPosition == -1) {
			/*	Integer curMaxDeps = -1;
				for (int j=0; j < sentArray.length;j++) {
					Set<Integer> dependents = depStruct.getDependents().get((j+1));
					if (dependents != null && dependents.size() > curMaxDeps) {
						rootPosition = (j+1);
						curMaxDeps = dependents.size();
					}
				}*/
				boolean foundWithHead = false;
				int ind = 1;
				while (!foundWithHead) {
					if (!headless.contains(ind+1)) {
						foundWithHead = true;
					}
					else {
						ind++;
					}
				}
		//		System.out.println("ind: "+ind);
				Stack<Integer> st = new Stack<Integer>();
				st.add(ind);
				boolean foundRoot = false;
				while (!st.isEmpty() && !foundRoot) {
					int curDep = st.pop();
					int curHead = depStruct.getHeads()[curDep];
				//	System.out.println(curDep+" "+curHead);
					if (curHead >=0) {
						st.add(curHead);
					}
					else {
						foundRoot = true;
						rootPosition = curDep;
					}
				}
			}
	//		if (rootPosition == -1 ) {
	//			rootPosition = 1;
	//		}
	//		System.out.println(rootPosition+" "+headless);
			Iterator<Integer> iter = headless.iterator();
			while (iter.hasNext()) {
				int curJ = iter.next();

				if (curJ+1 != rootPosition) {
					sentArray[curJ][6] = String.valueOf(rootPosition);
					sentArray[curJ][7] = "NMOD";
				//	sentArray[curJ][7] = "nmod__adj";
				//	String mostFreqLabel = super.findTheMostFreqLabel(sentArray[curJ][3]);
				//	sentArray[curJ][7] = mostFreqLabel;
				}
				else {
					sentArray[curJ][6] = "0";
					sentArray[curJ][7] = "ROOT";
				//	sentArray[curJ][7] = "main";
					rootPosition = curJ+1;
				}
			}			
		}
	}
//nmod__adj
	public String findOutCorrectLabel(int j, int i, String[][] sentArray) {
		// 1 - left arc
		// 2 - right arc
		// 3 - shift
		String label = "";
		if (Integer.valueOf(sentArray[j-1][6]) == i) {
			label = "j";
		/*	if (Integer.valueOf(sentArray[j-1][6]) == 0) {
				label = "root";
			}*/
			return label;
		}	
		else if (i == 0) {
			label = "shift";
			return label;
		}
		else if (Integer.valueOf(sentArray[i-1][6]) == j) {
			label = "i";
			return label;
		}
		else {
			label = "shift";
		}
		if (label.equals("shift")) {
			boolean terminate = true;
			if (Integer.valueOf(sentArray[j-1][6]) < i) {
				terminate = false;
			}
			for (int k=i; k > 1;k--) {
				if (Integer.valueOf(sentArray[k-1][6]) == j) {
					terminate = false;
				}
			}
			if (terminate) {
				label = "terminate";
			}
		}
		return label;
	}

	public String findOutCorrectLabelCombined(int j, int i, String[][] sentArray) {
		// 1 - left arc
		// 2 - right arc
		// 3 - shift
		String label = "";
		if (Integer.valueOf(sentArray[j-1][6]) == i) {
			label = "j";
		/*	if (Integer.valueOf(sentArray[j-1][6]) == 0) {
				label = "root";
			}*/
			label+= "#"+sentArray[j-1][7];
			return label;
		}	
		else if (i == 0) {
			label = "shift";
			return label;
		}
		else if (Integer.valueOf(sentArray[i-1][6]) == j) {
			label = "i";
			label+= "#"+sentArray[i-1][7];
			return label;
		}
		else {
			label = "shift";
		}
		if (label.equals("shift")) {
			boolean terminate = true;
			if (Integer.valueOf(sentArray[j-1][6]) < i) {
				terminate = false;
			}
			for (int k=i; k > 1;k--) {
				if (Integer.valueOf(sentArray[k-1][6]) == j) {
					terminate = false;
				}
			}
			if (terminate) {
				label = "terminate";
			}
		}
		return label;
	}	
	
	@Override
	// XXX GN: This one is used in training
	
	public List<FeatureVector> processCombined(Sentence sent,FeatureModel fm, boolean noLabels) {
		List<FeatureVector> fvParserList = new ArrayList<FeatureVector>();
		String[][] sentArray = sent.getSentArray();
	//	double rootProbabilities[] = new double[sentArray.length];
		DependencyStructure curDepStruct = new DependencyStructure(sentArray.length);
		CovingtonFeatureModel fm2 = (CovingtonFeatureModel) fm;
		fm2.initializeStaticFeaturesCombined(sent,false);
		// XXX GN: for all possible pairs (i,j) 
		// determine all permissible states and for these its feature vector
		for (int j = 1; j < sentArray.length+1; j++) {
			for (int i = j-1; i >= 0; i--) {
				CovingtonParserState ps = new CovingtonParserState(j,i,sent,curDepStruct);
				ps.checkPermissibility();
				if (ps.isPermissible()) {
					super.plus();
					FeatureVector fvParser = fm2.applyCombined(ps, true, noLabels);	
					// GN: determine the name of the operation/class using the information from
					//		relevant tokens of the sentence
					// 		and create the class/label name from it (an instance of left-arc, right-arc, shift, terminate)
					String label = findOutCorrectLabelCombined(j, i, sentArray);
			//		label = indianLabel(label,sentArray, j,i);
			//		System.out.println(i+" "+j+" "+label+" "+fvParser);
					String labelTrans = "";
					if (label.contains("#")) {
						labelTrans = label.split("#")[0];
					}
					
					if (labelTrans.equals("j") ) {								
							String depRel = sentArray[j-1][7];					
							sentArray[j-1][9] = depRel; 
							curDepStruct.addDependency(new Dependency(j,i,depRel));
						}
						else if	(labelTrans.equals("i")) {
							String depRel = sentArray[i-1][7];
							sentArray[i-1][9] = depRel;
							curDepStruct.addDependency(new Dependency(i,j,depRel));
						}
						else if (label.equals("terminate")) {							
							i = -1;
						}
						fm2.getAlphabetParser().addLabel(label);
						fvParser.setLabel(label);								
						fvParserList.add(fvParser);										
				}
			}
			
		}
	//	postprocess(sentArray, sent,curDepStruct);
		return fvParserList;
	}

	private String indianLabel(String label,String[][] sentArray, int j, int i) {
		if (label.equals("shift") || label.equals("terminate")) {
			return label;
		}
		else {
			String labelTrans = "";
			String depRel = "";
			if (label.contains("#")) {
				labelTrans = label.split("#")[0];
				depRel = label.split("#")[1];
				if (depRel.equals("ras-k1")) {
					if (labelTrans.equals("j")) {
						sentArray[j-1][7] = "k1";
					}	
					if (labelTrans.equals("i")) {
						sentArray[i-1][7] = "k1";
					}
				}
				if (depRel.equals("k3")) {
					if (labelTrans.equals("j")) {
						sentArray[j-1][7] = "k7";
					}	
					if (labelTrans.equals("i")) {
						sentArray[i-1][7] = "k7";
					}
				}
				if (depRel.equals("k2u")) {
					if (labelTrans.equals("j")) {
						sentArray[j-1][7] = "k2";
					}	
					if (labelTrans.equals("i")) {
						sentArray[i-1][7] = "k2";
					}
				}
				if (depRel.equals("k2s")) {
					if (labelTrans.equals("j")) {
						sentArray[j-1][7] = "k2";
					}	
					if (labelTrans.equals("i")) {
						sentArray[i-1][7] = "k2";
					}
				}
				if (depRel.equals("k5")) {
					if (labelTrans.equals("j")) {
						sentArray[j-1][7] = "k7";
					}	
					if (labelTrans.equals("i")) {
						sentArray[i-1][7] = "k7";
					}
				}
				if (depRel.equals("k3")) {
					if (labelTrans.equals("j")) {
						sentArray[j-1][7] = "k7";
					}	
					if (labelTrans.equals("i")) {
						sentArray[i-1][7] = "k7";
					}
				}
				if (depRel.equals("mk1")|| depRel.equals("r6-k2s")|| depRel.equals("rtu")|| depRel.equals("ras-k1u")|| depRel.equals("jjmod__relc")
						|| depRel.equals("nmod_adj")|| depRel.equals("k3u")|| depRel.equals("r6-k2u")|| depRel.equals("k7tu")|| depRel.equals("nmod__neg")
						|| depRel.equals("pof_idiom")|| depRel.equals("ras_k7")|| depRel.equals("k4u")|| depRel.equals("k7pu")|| depRel.equals("pof__inv")
						|| depRel.equals("k7u")|| depRel.equals("lwg_vaux")|| depRel.equals("psp__cl")|| depRel.equals("rad")|| depRel.equals("ras-r6-k2")
						|| depRel.equals("ras-pof")|| depRel.equals("mod__cc")|| depRel.equals("lwg__unk")|| depRel.equals("ras-k7")|| depRel.equals("ras-k7p")) {
					if (labelTrans.equals("j")) {
						sentArray[j-1][7] = "nmod__adj";
					}	
					if (labelTrans.equals("i")) {
						sentArray[i-1][7] = "nmod__adj";
					}
				}
				if (labelTrans.equals("j")) {
					label = labelTrans+"#"+sentArray[j-1][7];
				}
				if (labelTrans.equals("i")) {
					label = labelTrans+"#"+sentArray[i-1][7];
				}
			}
		}
		return label;
	}

	@Override
	public void processCombined(Sentence sent, FeatureModel fm,	boolean noLabels, HashMap<String, String> splitMap) {
		String[][] sentArray = sent.getSentArray();
		sent.setRootPosition(-1);
			DependencyStructure curDepStruct = new DependencyStructure(sentArray.length);
			CovingtonFeatureModel fm2 = (CovingtonFeatureModel) fm;
			fm2.initializeStaticFeaturesCombined(sent,false);
			for (int j = 1; j < sentArray.length+1; j++) {
				for (int i = j-1; i >= 0; i--) {
					CovingtonParserState ps = new CovingtonParserState(j,i,sent,curDepStruct);
					ps.checkPermissibility();
					if (ps.isPermissible()) {
						super.plus();
						FeatureVector fvParser = fm2.applyCombined(ps, false, noLabels);
					//	System.out.println(fvParser);
						String mName = "";
						if (splitMap.get(fvParser.getFeature("pj").getFeatureString()) == null) {
							List<String> mNames = new ArrayList<String>(splitMap.values());
							mName = mNames.get(0);
						}
						else {
							mName = splitMap.get(fvParser.getFeature("pj").getFeatureString());
						}
						Model curModel = this.getParser().getSplitModelMap().get(mName);
				//		mName = "splitA"+mName.substring(5);
				//		Alphabet curAlphabet = this.getParser().getSplitAlphabetsMap().get(mName);
				//		HashMap<String,Integer> indexMap = curAlphabet.getValueToIndexMap();
					//	System.out.println(mName+" "+curAlphabet);
				/*		FeatureNode[] fn = fvParser.getLiblinearRepresentation(false,false,fm2.getAlphabetParser());
						System.out.println(curModel+" ->"+mName);
						System.out.println(this.getParser().getSplitModelMap());*/
				//		System.out.println(+" "+curModel+" "+fm2+" "+fm2.getAlphabetParser());
						int labelInt = (int) Linear.predict(curModel, fvParser.getLiblinearRepresentation(false,false,fm2.getAlphabetParser()));	
		/* new			List<Integer> indexes = new ArrayList<Integer>();
						for (int m=0; m < fvParser.getfList().size();m++) {
							Feature f = fvParser.getfList().get(m);
							String fString = f.getFeatureString();
							Integer fIndex = indexMap.get(fString);
							if (fIndex != null) {
								indexes.add(fIndex);
							}
						}
						FeatureNode[] fn = new FeatureNode[indexes.size()];
						for (int m=0; m < indexes.size();m++) {
							fn[m] = new FeatureNode(indexes.get(m),1);
						}
						int labelInt = Linear.predict(curModel, fn);*/
				//		System.out.println();
					//	String label = curAlphabet.getIndexLabelArray()[labelInt];
						String label = fm2.getAlphabetParser().getIndexLabelArray()[labelInt];
					//	System.out.println(j+" "+i+" "+label+" "+fvParser);
						String labelTrans = "";
						String labelDepRel = "";
						if (label.contains("#")) {
							labelTrans = label.split("#")[0];
							labelDepRel = label.split("#")[1];
						}
					//	System.out.println(j+" "+i+" "+ps.isL1Permissible()+" "+ps.isL2Permissible());
						if (labelTrans.equals("j") && ps.isL1Permissible()) {
								sentArray[j-1][6] = String.valueOf(i);
								String depRel = labelDepRel;
								sentArray[j-1][7] = depRel;
								sentArray[j-1][9] = depRel;
								curDepStruct.addDependency(new Dependency(j,i,labelDepRel));
								if (i == 0) {								
									sent.setRootPosition(j);
								}
						//		String curPos = sentArray[j-1][3];
						//		String key = curPos+"###"+depRel;								
						//		super.increaseCount(key);
							}
							else if	(labelTrans.equals("i") && ps.isL2Permissible()) {					
								sentArray[i-1][6] = String.valueOf(j);
								String depRel = labelDepRel;
								sentArray[i-1][7] = depRel;
								sentArray[i-1][9] = depRel;
								curDepStruct.addDependency(new Dependency(i,j,labelDepRel));
							//	String curPos = sentArray[i-1][3];
							//	String key = curPos+"###"+depRel;
							//	super.increaseCount(key);
							}
							else if (label.equals("terminate")) {
								i = -1;
							}
						}
					
					}
				}
				postprocess(sentArray, sent,curDepStruct);	
	}

	

}
