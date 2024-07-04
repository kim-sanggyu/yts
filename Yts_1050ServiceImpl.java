package module.mis.yts.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.sql.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import module.common.util.CommonUtil;
import module.mis.yts.com.YtsAction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;

import com.component.upload.IFileUploadManagement;
import com.framework.core.IRequest;
import com.framework.core.IResponse;

/*
 * 설    명 : 근로소득지급명세서 생성
 * 작 성 자 : 2008.09.17 황인철
 * 수 정 일 : 2016.11.23
 * 수 정 일 : 2021.07.22 김상규
 * 수정내역 : 가독성 개선
 * 수 정 일 : 2022.08.18 김상규
 * 수정내역 : 가독성 개선
 */

//수정사항
//의료비 코드 변경 반영
//한글짤리는 문제
//이상있는 경우 먼저 점검

/*
 * 주택관련 7천만원 세액계산시 반영
 *
 */

public class Yts_1050 extends YtsAction{
	private static final Log LOG = LogFactory.getLog(Yts_1050.class);
	private static IFileUploadManagement fum = (IFileUploadManagement) getComponent(IFileUploadManagement.class);

	@Override
	public IResponse onLoad(IRequest request) throws Exception {
		Map map = getLoginMap(request);
		request.addRequestObject("params",map);
		return null;
	}

	public IResponse run(IRequest request) throws Exception{
		Map map = getLoginMap(request);
		String mode = (String)map.get("mode");

		if     (mode.equals("getListCorp"))		{ return getListCorp(request); 		}
		else if(mode.equals("checkNoCalcData"))	{ return checkNoCalcData(request);	}

		else if(mode.equals("getWorkFile"))		{ return getWorkFile(request);		}
		else if(mode.equals("getMediFile"))		{ return getMediFile(request);		}

		else if(mode.equals("doSearchError"))	{ return doSearchError(request);	}

		else return null;


	}

	//목록데이터
	public IResponse getListCorp(IRequest request) throws Exception{
		Map map = getLoginMap(request);
		String mode	= (String)map.get("mode");
		String cmd	= (String)map.get("cmd");

		List list = new ArrayList<String>();
		list = getList("yts.main.Yts1050.getListCorp", map);
		String json = new ObjectMapper().writeValueAsString(list);

		return write(mode+"▥"+cmd+"▥"+json);
	}

	//main에는 conf_yn="Y"로 되어 있으나 calc에는 해당 record가 없는 오류 check
	public IResponse checkNoCalcData(IRequest request) throws Exception{
		Map map = getLoginMap(request);
		String mode	= (String)map.get("mode");
		String cmd	= (String)map.get("cmd");

		Map errList = (Map)getItem("yts.main.Yts1050.getErrList_RecordC", map);
		String json = (String) errList.get("ERRLIST");

		return write(mode+"▥"+cmd+"▥"+json);
	}

	//(개발중...)check
	public IResponse doSearchError(IRequest request) throws Exception{
		Map map = getLoginMap(request);
		String mode	= (String)map.get("mode");
		String cmd	= (String)map.get("cmd");

		List list = new ArrayList<String>();
		list = getList("yts.main.Yts1050.getFileError", map);
		String json = new ObjectMapper().writeValueAsString(list);

		return write(mode+"▥"+cmd+"▥"+json);
	}

	//(개발중...)check
	public IResponse checkError(IRequest request) throws Exception{
		Map map = getLoginMap(request);
		String mode	= (String)map.get("mode");
		String cmd	= (String)map.get("cmd");

		String json = null;
		/*
		B-RECORD의 종근무지 숫자와 D-RECORD의 전체 수

		*/
		return write(mode+"▥"+cmd+"▥"+json);
	}





	//---------------------------------------------------------------------------------------------------------------
	// 근로소득 파일 생성
	//---------------------------------------------------------------------------------------------------------------

	private IResponse getWorkFile(IRequest request) throws Exception{
		//if(true) throw new Exception("테스트!!!");

		Map sessMap =  getLoginMap(request);
		String mode	= (String)sessMap.get("mode");
		String cmd	= (String)sessMap.get("cmd");



		//(내용검증用)화면에서 조회한 금액을 저장************************************************************************
		int  empYCnt 		= Integer.parseInt	(String.valueOf(sessMap.get("EMP_Y_CNT")));		//확정한 인력
		long totPayAmt		= Long.parseLong	(String.valueOf(sessMap.get("TOT_PAY_AMT"))); 	//총급여
		long taxSum			= Long.parseLong	(String.valueOf(sessMap.get("TAX_SUM")));		//총세액
		//***************************************************************************************************************




		String withhldResper = (String)sessMap.get("WITHHLD_RESPER");

		//반복을 위해 사용하는 변수들(E, F, G레코드), 사용 전 초기화 필수
		int recordSeq      = 0; //동일 부모 레코드에 자식 레코드가 여럿 있을때 자식 레코드에 일련번호 부여 용도
		int realCount      = 0; //실제 테이블에서 찾아온 데이터 갯수
		int countPerRecord = 0; //한 레코드에 몇개의 데이터를 담을지를 결정하는 수
		int totalCount     = 0; //realCount를 countPerRecord의 배수로 만드는 수

		String msg 		   = "";

		sessMap.put("Dir", "Dir"); 	//건기연 공통변경으로 인하여 변수처리

		String strSubmitDt  = ((String )sessMap.get("SUBMIT_DT")).replace("-","");
		String strDeptNm    = ( String )sessMap.get("DEPT_NM"  );
		String strEmpNm     = ( String )sessMap.get("EMP_NM"   );
		String strTelNo     = ( String )sessMap.get("TEL_NO"   );
		String strYy     	= ( String )sessMap.get("YY"       );

		List wrk_list =	getList("yts.main.Yts1050.wrkCorp", sessMap);
		//실제 오류발생 가능성은 없음
		if(wrk_list.size() != 1) { throw new Exception("파일생성 작업 중 오류가 발생하였습니다. \n\n원천징수의무자(" + withhldResper + ") 정보를 찾을 수 없거나 여러 건 존재합니다."); }
		Map cMap = (Map)wrk_list.get(0);

		String SUBMITTER_TAX_CD	= getStrInMap(cMap, "TAX_CD"     );
		String HOMETAX_ID		= getStrInMap(cMap, "HOMETAX_ID" );
		String SUBMITTER_REG_NO	= getStrInMap(cMap, "BUSI_REG_NO");
		String SUBMITTER_CORP_NM= getStrInMap(cMap, "CORP_NM"    );

		String strlcnsUntTaxYn	= getStrInMap(cMap, "LCNS_UNT_TAX_YN"); //사업자단위과세자 여부[1/2] 2017년 추가
		String strSubLcnsSeq	= getStrInMap(cMap, "SUB_LCNS_SEQ"   );

		//1.사업자단위과세자 여부가 여인 경우{[C171]=‘1’} 공란이면 오류
		//2.사업자단위과세자 여부가 부인 경우{[C171]=‘2’} 공란이 아니면 오류, 사업자단위과세자인 경우, 종사업장 일련번호 기재(주사업장은 ‘0000’ 기재)

		if(strlcnsUntTaxYn.equals("2")) { strSubLcnsSeq = ""; }
		else							{ strSubLcnsSeq = makeStr("9", 4, strSubLcnsSeq); } //주사업장이면 '0000' 그렇지 않으면 '0001'

		int iIssuerCnt          = 1;	// 2020년까지 사용한 기존 원천징수의무자 수
		//(?????)정산연도 기준의 cnt로 수정
		//int withHoldingAgentCnt = (Integer) getItem("yts.main.Yts1050.getNumberOfWHA", sessMap);		// 원천징수의무자수 = A15 , 2021년추가


		/*
		System.out.println("empYCnt             " + empYCnt);
		System.out.println("totPayAmt           " + totPayAmt);
		System.out.println("taxSum              " + taxSum);
		System.out.println("withhldResper       " + withhldResper);
		System.out.println("YY                  " + strYy);
		System.out.println("strSubmitDt         " + strSubmitDt);
		System.out.println("EMP_NM              " + strEmpNm);
		System.out.println("strDeptNm           " + strDeptNm);
		System.out.println("strTelNo            " + strTelNo);

		System.out.println("HOMETAX_ID          " + HOMETAX_ID);
		System.out.println("SUBMITTER_TAX_CD    " + SUBMITTER_TAX_CD);
		System.out.println("SUBMITTER_REG_NO    " + SUBMITTER_REG_NO);
		System.out.println("SUBMITTER_CORP_NM   " + SUBMITTER_CORP_NM);
		*/





		//파일오픈 ----------------------------------------------------------------------------------------------------------
		Map resMap = (Map)getItem("yts.main.Yts1050.getfilenm", sessMap);
		//실제 오류발생 가능성은 없음
		if(resMap == null) { throw new Exception("파일생성 작업 중 오류가 발생하였습니다. \n\n파일정보를 찾을 수 없습니다.\n(원천징수의무자코드: "+ withhldResper +")"); }
		String fileNm 	= (String)resMap.get("FILENAME");
		Writer bw 		= openFile(fileNm);


		//A레코드[제출자(대리인) 레코드] ------------------------------------------------------------------------------------

		bw.write(
				makeStr("X",   1, "A"                                                       ) + // A1    【자료관리번호】레코드구분
				makeStr("9",   2, "20"                                                      ) + // A2    【자료관리번호】자료구분
				makeStr("X",   3, SUBMITTER_TAX_CD                                          ) + // A3    【자료관리번호】세무서코드
				makeStr("9",   8, strSubmitDt                                               ) + // A4    【자료관리번호】제출연월일
				makeStr("9",   1, "2"                                                       ) + // A5    【제출자】제출자구분
				makeStr("X",   6, " "                                                       ) + // A6    【제출자】세무대리인관리번호
				makeStr("X",  20, HOMETAX_ID                                                ) + // A7    【제출자】홈택스ID
				makeStr("X",   4, "9000"                                                    ) + // A8    【제출자】세무프로그램코드
				makeStr("X",  10, SUBMITTER_REG_NO                                          ) + // A9    【제출자】사업자등록번호
				makeStr("X",  60, SUBMITTER_CORP_NM                                         ) + // A10   【제출자】법인명(상호)
				makeStr("X",  30, strDeptNm                                                 ) + // A11   【제출자】담당자부서
				makeStr("X",  30, strEmpNm                                                  ) + // A12   【제출자】담당자성명
				makeStr("X",  15, strTelNo                                                  ) + // A13   【제출자】담당자전화번호
				makeStr("X",   4, strYy                                                     ) + // A14   【제출내역】귀속연도
				makeStr("9",   5, String.valueOf(iIssuerCnt)                                ) + // A15   【제출내역】신고의무자수
				makeStr("9",   3, "101"                                                     ) + // A16   【제출내역】사용한글코드
				makeStr("X",1808, " "                                                       ) + // A17   【제출내역】공란

			"\n"
		);


		//B레코드[원천징수의무자별 집계 레코드] -----------------------------------------------------------------------------

		List list_WRK_B = getList("yts.main.Yts1050.getWRKListB", sessMap);
		//CONF_YN="Y"가 한건도 없는 경우, 화면에서 오류CHECK를 하기 때문에 실제 여기서 오류발생할 가능성은 없음
		if(list_WRK_B.size() != 1) throw new Exception("파일생성 작업 중 오류가 발생하였습니다. \n\nB레코드(원천징수의무자별 집계 레코드)자료에 오류가 있습니다. ");
		Map	map_B = (Map)list_WRK_B.get(0);

		String TAX_CD  		= (String)map_B.get("TAX_CD");
		String BUSI_REG_NO  = (String)map_B.get("BUSI_REG_NO");
		String CORP_NM 		= (String)map_B.get("CORP_NM");




		//(내용검증用)화면에서 조회된 금액과 비교하기 위해 B레코드 검색값 저장*******************************************
		int B_mainCnt 	= Integer.parseInt(String.valueOf(map_B.get("MAIN_CNT")));
		long B_totPayAmt= Long.parseLong(getStrInMap(map_B, "TOT_PAY_AMT"));
		long B_taxSum	= Long.parseLong(getStrInMap(map_B, "RES_INCM_TAX")) + Long.parseLong(getStrInMap(map_B, "RES_INHABT_TAX"))	+ Long.parseLong(getStrInMap(map_B, "FST_DEC_TAX"));
		int B_subCnt  	= Integer.parseInt(String.valueOf(map_B.get("SUB_CNT")));
		//***************************************************************************************************************




		//화면의 확정인원  (empYCnt)과    b-record의 소득자 합계 인원수(B_mainCnt)  가 일치해야함
		//화면의 총급여    (totPayAmt)와  b-record의 급여 총합계       (B_totPayAmt)가 일치해야함
		//화면의 총결정세액(taxSum)과     b-record의 결정세액의 총합계 (B_taxSum)   가 일치해야함

		//내용검증 CHECK ------------------------------------------------------------------------------------------------
		System.out.println  ("\n>>화면(screen) 조회 금액과 B-record 검색 시 금액 비교================================================\n");
		System.out.println(String.format("소득자수 %,15d screen %,15d B-record %,15d Gap", empYCnt		,B_mainCnt		,empYCnt	- B_mainCnt));
		System.out.println(String.format("총급여   %,15d screen %,15d B-record %,15d Gap", totPayAmt	,B_totPayAmt	,totPayAmt	- B_totPayAmt));
		System.out.println(String.format("결정세액 %,15d screen %,15d B-record %,15d Gap", taxSum		,B_taxSum		,taxSum		- B_taxSum));
		System.out.println("");

		msg = "";
		if((empYCnt  != B_mainCnt))    						{msg = "파일생성 작업 중 오류가 발생하였습니다. \n\n화면에 조회된 확정인원수와 \n파일에 집계할 확정인원수가 일치하지 않습니다. \n연말정산팀에 문의하세요."; 			throw new Exception(msg); }
		if(totPayAmt != B_totPayAmt || taxSum != B_taxSum)	{msg = "파일생성 작업 중 오류가 발생하였습니다. \n\n화면에 조회된 총급여/결정세액과 \n파일에 집계할 총급여/결정세액이 일치하지 않습니다. \n연말정산팀에 문의하세요.";	throw new Exception(msg); }


		bw.write(
				makeStr("X",   1, "B"                                                       ) + // B1    【자료관리번호】레코드구분
				makeStr("9",   2, "20"                                                      ) + // B2    【자료관리번호】자료구분
				makeStr("X",   3, TAX_CD                                                    ) + // B3    【자료관리번호】세무서코드
				makeStr("9",   6, "1"                                                       ) + // B4    【자료관리번호】일련번호
				makeStr("X",  10, BUSI_REG_NO                                               ) + // B5    【원천징수의무자】사업자등록번호
				makeStr("X",  60, CORP_NM                                                   ) + // B6    【원천징수의무자】법인명(상호)
				makeStr("X",  30, getStrInMap(map_B, "REPRES"                           )   ) + // B7    【원천징수의무자】대표자(성명)
				makeStr("X",  13, getStrInMap(map_B, "CORP_NO"                          )   ) + // B8    【원천징수의무자】주민(법인)등록번호
				makeStr("X",   4, strYy                                                     ) + // B9    【제출내역】귀속연도
				makeStr("9",   7, getStrInMap(map_B, "MAIN_CNT"                         )   ) + // B10   【제출내역】주(현)근무처(C레코드)수
				makeStr("9",   7, getStrInMap(map_B, "SUB_CNT"                          )   ) + // B11   【제출내역】종(전)근무처(D레코드)수
				makeStr("9",  14, getStrInMap(map_B, "TOT_PAY_AMT"                      )   ) + // B12   【제출내역】총급여총계
				makeStr("9",  13, getStrInMap(map_B, "RES_INCM_TAX"                     )   ) + // B13   【제출내역】결정세액(소득세)총계
				makeStr("9",  13, getStrInMap(map_B, "RES_INHABT_TAX"                   )   ) + // B14   【제출내역】결정세액(지방소득세)총계
				makeStr("9",  13, getStrInMap(map_B, "FST_DEC_TAX"                      )   ) + // B15   【제출내역】결정세액(농특세)총계
				makeStr("9",  13, getStrInMap(map_B, "TAX_SUM"                          )   ) + // B16   【제출내역】결정세액총계
				makeStr("9",   1, "1"                                                       ) + // B17   【제출내역】제출대상기간코드
				makeStr("X",1800, " "                                                       ) + // B18   【제출내역】공란

			"\n"
		);





		//C레코드[주(현)근무처 레코드] 소득신고자 검색
		List list_Emps = getList("yts.main.Yts1050.getWRKListEmps", sessMap);

		//conf_yn = Y인 소득자의 세액계산 데이터가 pay_wrk_calc에 없을 때(step4에서 calc 프로세스 작업 중 오류가 발생한 경우) 둘 간 차이가 날 수 있음
		if(B_mainCnt != list_Emps.size()){
			System.out.println(String.format(">>확정자 인원수에 오류가 있습니다 → B_mainCnt(getWRKListB): %d, C_mainCnt(getWRKListEmps): %d\n", B_mainCnt, list_Emps.size()));
			Map errList = (Map)getItem("yts.main.Yts1050.getErrList_RecordC", sessMap);
			msg = "파일생성 작업 중 오류가 발생하였습니다. \n\n사업자등록번호 [" + BUSI_REG_NO + "]"
				+"\n세액계산되지 않고 제출(확정)된 소득자가 있습니다."
				+"\n아래 인력에 대해 조치 후 파일생성을 하시기 바랍니다.\n조치 방법은 연말정산팀에 문의하세요.\n\n(최대 10명까지만 표시)\n" + errList.get("ERRLIST");
			throw new Exception(msg);
		}



		//(내용검증用)화면에서 조회된 금액과 비교하기 위해 C레코드 검색값 저장************************************************************************
		int  C_mainCnt   = 0;
		long C_totPayAmt = 0;
		long C_taxSum 	 = 0;
		int  C_subCnt	 = 0;
		//********************************************************************************************************************************************



		for (Iterator all_Emp = list_Emps.iterator(); all_Emp.hasNext();){
			Map map_Emp 	= (Map) all_Emp.next();
			String CNTC 	= getStrInMap(map_Emp, "CNTC");
			String CALC_NO	= getStrInMap(map_Emp, "CALC_NO");
			String NM 		= getStrInMap(map_Emp, "NM");
			String EMP_NO 	= getStrInMap(map_Emp, "EMP_NO");
			String RES_NO 	= getStrInMap(map_Emp, "RES_NO");
			String HOME_CLS = getStrInMap(map_Emp, "HOME_CLS");







			//if("10".equals(CNTC)) break;








			// C레코드[주(현)근무처 레코드] ------------------------------------------------------------------------------------

			//앞에서 오류 CHECK를 했기에 여기서 오류발생 가능성은 없음
			List list_C_main = getList("yts.main.Yts1050.getWRKListC_MAIN", map_Emp);
			if(list_C_main.size() != 1) {msg = "파일생성 작업 중 오류가 발생하였습니다. \n\n주(현)근무처(MAIN)에서 해당 소득자("+ CALC_NO + ")의 자료를 찾을 수 없습니다.";throw new Exception(msg);}
			HashMap map_C_main = (HashMap)list_C_main.get(0);

			//앞에서 오류 CHECK를 했기에 여기서 오류발생 가능성은 없음
			List list_C_calc = getList("yts.main.Yts1050.getWRKListC_CALC", map_Emp);
			if(list_C_calc.size() != 1) {msg = "파일생성 작업 중 오류가 발생하였습니다. \n\n주(현)근무처(CALC)에서 해당 소득자("+ CALC_NO + ")의 자료를 찾을 수 없습니다.";throw new Exception(msg);}
			HashMap map_C_calc = (HashMap)list_C_calc.get(0);

			//(내용검증用)화면에서 조회된 금액과 비교하기 위해 C레코드 검색값 저장************************************************************************
			int subCnt	 = Integer.parseInt(getStrInMap(map_C_main, "SUB_CNT"));
			C_mainCnt	+= 1;
			C_subCnt	+= subCnt;
			C_totPayAmt += Long.parseLong(getStrInMap(map_C_calc, "TOT_PAY_AMT"));
			C_taxSum	+= Long.parseLong(getStrInMap(map_C_calc, "RES_INCM_TAX"))
						+  Long.parseLong(getStrInMap(map_C_calc, "RES_INHABT_TAX"))
						+  Long.parseLong(getStrInMap(map_C_calc, "FST_DEC_TAX"));
			//********************************************************************************************************************************************

			bw.write(
					makeStr("X",   1, "C"                                                       ) + // C1     【자료관리번호】레코드구분
					makeStr("9",   2, "20"                                                      ) + // C2     【자료관리번호】자료구분
					makeStr("X",   3, TAX_CD                                                    ) + // C3     【자료관리번호】세무서코드
					makeStr("9",   6, CNTC                                                      ) + // C4     【자료관리번호】일련번호
					makeStr("X",  10, BUSI_REG_NO                                               ) + // C5     【원천징수의무자】③사업자등록번호
					makeStr("9",   2, String.valueOf(subCnt                                 )   ) + // C6     【소득자(근로자)】종(전)근무처수
					makeStr("9",   1, getStrInMap(map_C_main, "HABT_CLS"                    )   ) + // C7     【소득자(근로자)】거주구분코드
					makeStr("X",   2, getStrInMap(map_C_main, "HABT_NTN_CD"                 )   ) + // C8     【소득자(근로자)】거주지국코드
					makeStr("9",   1, getStrInMap(map_C_main, "FRGN_TAX_CLS"                )   ) + // C9     【소득자(근로자)】외국인단일세율적용여부
					makeStr("X",   1, getStrInMap(map_C_main, "FRN_CORP_EMP_YN"             )   ) + // C10    【소득자(근로자)】외국법인소속파견근로자여부
					makeStr("X",  30, NM                                                        ) + // C11    【소득자(근로자)】⑥성명
					makeStr("9",   1, HOME_CLS                                                  ) + // C12    【소득자(근로자)】내･외국인구분코드
					makeStr("X",  13, RES_NO                                                    ) + // C13    【소득자(근로자)】⑦주민등록번호
					makeStr("X",   2, getStrInMap(map_C_main, "EMP_NTN_CD"                  )   ) + // C14    【소득자(근로자)】국적코드
					makeStr("X",   1, getStrInMap(map_C_main, "HOUSE_HLDR_YN"               )   ) + // C15    【소득자(근로자)】세대주여부
					makeStr("X",   1, getStrInMap(map_C_main, "KEEP_PS"                     )   ) + // C16    【소득자(근로자)】연말정산구분
					makeStr("X",   1, strlcnsUntTaxYn                                           ) + // C17    【소득자(근로자)】③-1사업자단위과세자여부
					makeStr("X",   4, strSubLcnsSeq                                             ) + // C18    【소득자(근로자)】③-2종사업장일련번호
					makeStr("X",   1, getStrInMap(map_C_main, "REL_WRKR_YN"                 )   ) + // C19    【소득자(근로자)】종교관련종사자여부
					makeStr("X",  10, BUSI_REG_NO                                               ) + // C20    【근무처별소득명세-주(현)근무처】⑩주현근무처-사업자등록번호
					makeStr("X",  60, CORP_NM                                                   ) + // C21    【근무처별소득명세-주(현)근무처】⑨주현근무처-근무처명
					makeStr("9",   8, getStrInMap(map_C_main, "BEL_FRM_DT"                  )   ) + // C22    【근무처별소득명세-주(현)근무처】⑪근무기간시작연월일
					makeStr("9",   8, getStrInMap(map_C_main, "BEL_TO_DT"                   )   ) + // C23    【근무처별소득명세-주(현)근무처】⑪근무기간종료연월일
					makeStr("9",   8, getStrInMap(map_C_main, "CUT_TAX_FRM_DT"              )   ) + // C24    【근무처별소득명세-주(현)근무처】⑫감면기간시작연월일
					makeStr("9",   8, getStrInMap(map_C_main, "CUT_TAX_TO_DT"               )   ) + // C25    【근무처별소득명세-주(현)근무처】⑫감면기간종료연월일
					makeStr("9",  11, getStrInMap(map_C_main, "PAY_AMT"                     )   ) + // C26    【근무처별소득명세-주(현)근무처】⑬급여
					makeStr("9",  11, getStrInMap(map_C_main, "BNS_AMT"                     )   ) + // C27    【근무처별소득명세-주(현)근무처】⑭상여
					makeStr("9",  11, getStrInMap(map_C_main, "AGREE_BONUS_AMT"             )   ) + // C28    【근무처별소득명세-주(현)근무처】⑮인정상여
					makeStr("9",  11, getStrInMap(map_C_main, "STOCK_OPT_BENEF"             )   ) + // C29    【근무처별소득명세-주(현)근무처】⑮-1주식매수선택권행사이익
					makeStr("9",  11, getStrInMap(map_C_main, "STOCK_DRAW_ACCT"             )   ) + // C30    【근무처별소득명세-주(현)근무처】⑮-2우리사주조합인출금
					makeStr("9",  11, getStrInMap(map_C_main, "EXCTV_RET_OVER"              )   ) + // C31    【근무처별소득명세-주(현)근무처】⑮-3임원퇴직소득금액한도초과액
					makeStr("9",  11, getStrInMap(map_C_main, "DUTY_INVNT_RWD"              )   ) + // C32    【근무처별소득명세-주(현)근무처】⑮-4직무발명보상금
					makeStr("9",  11, "0"                                                       ) + // C33    【근무처별소득명세-주(현)근무처】공란
					makeStr("9",  11, "0"                                                       ) + // C34    【근무처별소득명세-주(현)근무처】공란
					makeStr("9",  11, getStrInMap(map_C_main, "SUM_AMT"                     )   ) + // C35    【근무처별소득명세-주(현)근무처】계
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_G01"                    )   ) + // C36    【주(현)근무처비과세소득및감면소득】G01-비과세학자금

					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H05"                    )   ) + // C37    【주(현)근무처비과세소득및감면소득】H05-경호･승선수당
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H06"                    )   ) + // C38    【주(현)근무처비과세소득및감면소득】H06-유아･초중등
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H07"                    )   ) + // C39    【주(현)근무처비과세소득및감면소득】H07-고등교육법
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H08"                    )   ) + // C40    【주(현)근무처비과세소득및감면소득】H08-특별법
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H09"                    )   ) + // C41    【주(현)근무처비과세소득및감면소득】H09-연구기관등
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H10"                    )   ) + // C42    【주(현)근무처비과세소득및감면소득】H10-기업부설연구소
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H14"                    )   ) + // C43    【주(현)근무처비과세소득및감면소득】H14-보육교사근무환경개선비
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H15"                    )   ) + // C44    【주(현)근무처비과세소득및감면소득】H15-사립유치원수석교사･교사의인건비
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H11"                    )   ) + // C45    【주(현)근무처비과세소득및감면소득】H11-취재수당
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H12"                    )   ) + // C46    【주(현)근무처비과세소득및감면소득】H12-벽지수당
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H13"                    )   ) + // C47    【주(현)근무처비과세소득및감면소득】H13-재해관련급여
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H16"                    )   ) + // C48    【주(현)근무처비과세소득및감면소득】H16-정부･공공기관지방이전기관종사자이주수당
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_H17"                    )   ) + // C49    【주(현)근무처비과세소득및감면소득】H17-종교활동비
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_I01"                    )   ) + // C50    【주(현)근무처비과세소득및감면소득】I01-외국정부등근무자
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_K01"                    )   ) + // C51    【주(현)근무처비과세소득및감면소득】K01-외국주둔군인등
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_M01"                    )   ) + // C52    【주(현)근무처비과세소득및감면소득】M01-국외근로100만원
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_M02"                    )   ) + // C53    【주(현)근무처비과세소득및감면소득】M02-국외근로300만원
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_M03"                    )   ) + // C54    【주(현)근무처비과세소득및감면소득】M03-국외근로
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_O01"                    )   ) + // C55    【주(현)근무처비과세소득및감면소득】O01-야간근로수당
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_Q01"                    )   ) + // C56    【주(현)근무처비과세소득및감면소득】Q01-출산보육수당
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_R10"                    )   ) + // C57    【주(현)근무처비과세소득및감면소득】R10-근로장학금
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_R11"                    )   ) + // C58    【주(현)근무처비과세소득및감면소득】R11-직무발명보상금
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_S01"                    )   ) + // C59    【주(현)근무처비과세소득및감면소득】S01-주식매수선택권
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_U01"                    )   ) + // C60    【주(현)근무처비과세소득및감면소득】U01-벤처기업주식매수선택권
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_Y02"                    )   ) + // C61ⓐ  【주(현)근무처비과세소득및감면소득】Y02-우리사주조합인출금50%
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_Y03"                    )   ) + // C61ⓑ  【주(현)근무처비과세소득및감면소득】Y03-우리사주조합인출금75%
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_Y04"                    )   ) + // C61ⓒ  【주(현)근무처비과세소득및감면소득】Y04-우리사주조합인출금100%
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_Y22"                    )   ) + // C62    【주(현)근무처비과세소득및감면소득】Y22-전공의수련보조수당
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T01"                    )   ) + // C63ⓐ  【주(현)근무처비과세소득및감면소득】T01-외국인기술자소득세감면(50%)
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T02"                    )   ) + // C63ⓑ  【주(현)근무처비과세소득및감면소득】T02-외국인기술자소득세감면(70%)
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T30"                    )   ) + // C64    【주(현)근무처비과세소득및감면소득】T30-성과공유중소기업경영성과급
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T40"                    )   ) + // C65ⓐ  【주(현)근무처비과세소득및감면소득】T40-중소기업청년근로자및핵심인력성과보상기금소득세감면
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T41"                    )   ) + // C65ⓑ  【주(현)근무처비과세소득및감면소득】T41-중견기업청년근로자및핵심인력성과보상기금소득세감면
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T42"                    )   ) + // C65ⓒ  【주(현)근무처비과세소득및감면소득】T42-중소기업청년근로자및핵심인력성과보상기금소득세감면
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T43"                    )   ) + // C65ⓓ  【주(현)근무처비과세소득및감면소득】T43-중견기업청년근로자및핵심인력성과보상기금소득세감면
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T50"                    )   ) + // C66    【주(현)근무처비과세소득및감면소득】T50-내국인우수인력국내복귀소득세감면
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T11"                    )   ) + // C67ⓐ  【주(현)근무처비과세소득및감면소득】T11-중소기업취업자소득세감면50%
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T12"                    )   ) + // C67ⓑ  【주(현)근무처비과세소득및감면소득】T12-중소기업취업자소득세감면70%
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T13"                    )   ) + // C67ⓒ  【주(현)근무처비과세소득및감면소득】T13-중소기업취업자소득세감면90%
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_T20"                    )   ) + // C68    【주(현)근무처비과세소득및감면소득】T20-조세조약상교직자감면
					makeStr("9",  10, "0"                                                       ) + // C69    【주(현)근무처비과세소득및감면소득】공란
					makeStr("9",  10, getStrInMap(map_C_main, "NTAX_SUM"                    )   ) + // C70    【주(현)근무처비과세소득및감면소득】비과세계
					makeStr("9",  10, getStrInMap(map_C_main, "RED_INCM_SUM"                )   ) + // C71    【주(현)근무처비과세소득및감면소득】감면소득계

					makeStr("9",  11, getStrInMap(map_C_calc, "TOT_PAY_AMT"                 )   ) + // C72    【정산명세】총급여
					makeStr("9",  10, getStrInMap(map_C_calc, "WORK_TAX"                    )   ) + // C73    【정산명세】근로소득공제
					makeStr("9",  11, getStrInMap(map_C_calc, "WORK_AMT"                    )   ) + // C74    【정산명세】근로소득금액
					makeStr("9",   8, getStrInMap(map_C_calc, "BASC_SUB_SELF_AMT"           )   ) + // C75    【기본공제】본인공제금액
					makeStr("9",   8, getStrInMap(map_C_calc, "BASC_SUB_MATE_AMT"           )   ) + // C76    【기본공제】배우자공제금액
					makeStr("9",   2, getStrInMap(map_C_calc, "BASC_SUB_FAMILY_CNT"         )   ) + // C77ⓐ  【기본공제】부양가족공제인원
					makeStr("9",   8, getStrInMap(map_C_calc, "BASC_SUB_FAMILY_AMT"         )   ) + // C77ⓑ  【기본공제】부양가족공제금액
					makeStr("9",   2, getStrInMap(map_C_calc, "ADD_SUB_OAT_CNT"             )   ) + // C78ⓐ  【추가공제】경로우대공제인원
					makeStr("9",   8, getStrInMap(map_C_calc, "ADD_SUB_OAT_AMT"             )   ) + // C78ⓑ  【추가공제】경로우대공제금액
					makeStr("9",   2, getStrInMap(map_C_calc, "ADD_SUB_HDC_PERS_CNT"        )   ) + // C79ⓐ  【추가공제】장애인공제인원
					makeStr("9",   8, getStrInMap(map_C_calc, "ADD_SUB_HDC_PERS_AMT"        )   ) + // C79ⓑ  【추가공제】장애인공제금액
					makeStr("9",   8, getStrInMap(map_C_calc, "ADD_SUB_LADY_AMT"            )   ) + // C80    【추가공제】부녀자공제금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ADD_SUB_SNGL_PRNT_AMT"       )   ) + // C81    【추가공제】한부모가족공제금액
					makeStr("9",  10, getStrInMap(map_C_calc, "NP_INSU_OBJ_AMT"             )   ) + // C82ⓐ  【연금보험료공제】국민연금보험료공제_대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "NP_INSU_AMT"                 )   ) + // C82ⓑ  【연금보험료공제】국민연금보험료공제_공제금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ETC_PEN_PUBL_OBJ_AMT"        )   ) + // C83ⓐ  【연금보험료공제】㉮공적연금보험료공제_공무원연금_대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ETC_PEN_PUBL_AMT"            )   ) + // C83ⓑ  【연금보험료공제】㉮공적연금보험료공제_공무원연금_공제금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ETC_PEN_MLTARY_OBJ_AMT"      )   ) + // C84ⓐ  【연금보험료공제】㉯공적연금보험료공제_군인연금_대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ETC_PEN_MLTARY_AMT"          )   ) + // C84ⓑ  【연금보험료공제】㉯공적연금보험료공제_군인연금_공제금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ETC_PEN_SCHL_OBJ_AMT"        )   ) + // C85ⓐ  【연금보험료공제】㉰공적연금보험료공제_사립학교교직원연금_대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ETC_PEN_SCHL_AMT"            )   ) + // C85ⓑ  【연금보험료공제】㉰공적연금보험료공제_사립학교교직원연금_공제금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ETC_PEN_POST_OBJ_AMT"        )   ) + // C86ⓐ  【연금보험료공제】㉱공적연금보험료공제_별정우체국연금_대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "ETC_PEN_POST_AMT"            )   ) + // C86ⓑ  【연금보험료공제】㉱공적연금보험료공제_별정우체국연금_공제금액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_IF_HLTH_INSU_OBJ_AMT"   )   ) + // C87ⓐ  【특별소득공제】㉮보험료-건강보험료(노인장기요양보험료포함)_대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_IF_HLTH_INSU_AMT"       )   ) + // C87ⓑ  【특별소득공제】㉮보험료-건강보험료(노인장기요양보험료포함)_공제금액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_IF_EMP_INSU_OBJ_AMT"    )   ) + // C88ⓐ  【특별소득공제】㉯보험료-고용보험료_대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_IF_EMP_INSU_AMT"        )   ) + // C88ⓑ  【특별소득공제】㉯보험료-고용보험료_공제금액
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_HOUSE_RALR_LENDER_AMT"    )   ) + // C89ⓐ  【특별소득공제】㉮주택자금_주택임차차입금원리금상환액_대출기관
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_HOUSE_RALR_HABT_AMT"      )   ) + // C89ⓑ  【특별소득공제】㉮주택자금_주택임차차입금원리금상환액_거주자
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF1_AMT"             )   ) + // C90ⓐ  【특별소득공제】㉯(2011년이전차입분)주택자금_장기주택저당차입금이자상환액_15년미만
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF2_AMT"             )   ) + // C90ⓑ  【특별소득공제】㉯(2011년이전차입분)주택자금_장기주택저당차입금이자상환액_15년~29년
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF3_AMT"             )   ) + // C90ⓒ  【특별소득공제】㉯(2011년이전차입분)주택자금_장기주택저당차입금이자상환액_30년이상
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF10_AMT"            )   ) + // C91ⓐ  【특별소득공제】㉯(2012년이후차입분,15년이상)고정금리･비거치식상환대출
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF20_AMT"            )   ) + // C91ⓑ  【특별소득공제】㉯(2012년이후차입분,15년이상)기타대출
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF30_AMT"            )   ) + // C92ⓐ  【특별소득공제】㉯(2015년이후차입분,상환기간15년이상)고정금리and비거치상환대출
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF40_AMT"            )   ) + // C92ⓑ  【특별소득공제】㉯(2015년이후차입분,상환기간15년이상)고정금리or비거치상환대출
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF50_AMT"            )   ) + // C92ⓒ  【특별소득공제】㉯(2015년이후차입분,상환기간15년이상)그밖의대출
					makeStr("9",   8, getStrInMap(map_C_calc, "SP_LH_LRSF60_AMT"            )   ) + // C92ⓓ  【특별소득공제】㉯(2015년이후차입분,상환기간10년∼15년)고정금리or비거치상환대출
					makeStr("9",  11, getStrInMap(map_C_calc, "SPCL_SA_CARF_AMT"            )   ) + // C93    【특별소득공제】기부금(이월분)
					makeStr("9",  11, "0"                                                       ) + // C94    【특별소득공제】공란
					makeStr("9",  11, "0"                                                       ) + // C95    【특별소득공제】공란
					makeStr("9",  11, getStrInMap(map_C_calc, "SPCL_SUB_AMT_SUM"            )   ) + // C96    【특별소득공제】계
					makeStr("9",  11, getStrInMap(map_C_calc, "BIA_AMT"                     )   ) + // C97    【특별소득공제】차감소득금액
					makeStr("9",   8, getStrInMap(map_C_calc, "OTO_PPF"                     )   ) + // C98    【그밖의소득공제】개인연금저축소득공제
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_SM_ETPR_AMT"             )   ) + // C99    【그밖의소득공제】소기업·소상공인공제부금
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_HOUSE_LOAN_SBSC_AMT"     )   ) + // C100   【그밖의소득공제】㉮주택마련저축소득공제_청약저축
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_HOUSE_LOAN_ALL_AMT"      )   ) + // C101   【그밖의소득공제】㉯주택마련저축소득공제_주택청약종합저축
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_HOUSE_LOAN_WRK_AMT"      )   ) + // C102   【그밖의소득공제】㉰주택마련저축소득공제_근로자주택마련저축
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_IU_ETC"                  )   ) + // C103   【그밖의소득공제】투자조합출자등소득공제
					makeStr("9",   8, getStrInMap(map_C_calc, "OTO_CARD_ETC"                )   ) + // C104   【그밖의소득공제】신용카드등소득공제
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_SU"                      )   ) + // C105   【그밖의소득공제】우리사주조합출연금
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_EMPL_MTN_WAGE_CUT"       )   ) + // C106   【그밖의소득공제】고용유지중소기업근로자소득공제
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_LONG_STOCK_SAVING"       )   ) + // C107   【그밖의소득공제】장기집합투자증권저축
					makeStr("9",  10, getStrInMap(map_C_calc, "OTO_YM_LONG_STOCK_SAVING"    )   ) + // C108   【그밖의소득공제】청년형장기집합투자증권저축
					makeStr("9",  10, "0"                                                       ) + // C109   【그밖의소득공제】공란
					makeStr("9",  11, getStrInMap(map_C_calc, "OTO_SUM"                     )   ) + // C110   【그밖의소득공제】그밖의소득공제계
					makeStr("9",  11, getStrInMap(map_C_calc, "SPCL_TOT_LMT_OV_AMT"         )   ) + // C111   【그밖의소득공제】소득공제종합한도초과액
					makeStr("9",  11, getStrInMap(map_C_calc, "TOT_PTB"                     )   ) + // C112   【그밖의소득공제】종합소득과세표준
					makeStr("9",  11, getStrInMap(map_C_calc, "PROD_TAX_AMT"                )   ) + // C113   【그밖의소득공제】산출세액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_IT_LAW"                   )   ) + // C114   【세액감면】소득세법
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_R_LAW"                    )   ) + // C115   【세액감면】조특법(제외)
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_R_LAW_CLAUS30"            )   ) + // C116   【세액감면】조특법제30조
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_TAX_TREATY"               )   ) + // C117   【세액감면】조세조약
					makeStr("9",  10, "0"                                                       ) + // C118   【세액감면】공란
					makeStr("9",  10, "0"                                                       ) + // C119   【세액감면】공란
					makeStr("9",  10, getStrInMap(map_C_calc, "TAX_CUT"                     )   ) + // C120   【세액감면】세액감면계
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_WIA"                      )   ) + // C121   【세액공제】근로소득세액공제
					makeStr("9",   2, getStrInMap(map_C_calc, "RT_HWC_CNT"                  )   ) + // C122ⓐ 【세액공제】㉮자녀세액공제인원
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_HWC_AMT"                  )   ) + // C122ⓑ 【세액공제】㉮자녀세액공제
					makeStr("9",   2, getStrInMap(map_C_calc, "RT_PER_CHI_CNT"              )   ) + // C123ⓐ 【세액공제】㉰출산･입양세액공제인원
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_PER_CHI_AMT"              )   ) + // C123ⓑ 【세액공제】㉰출산･입양세액공제
					makeStr("9",  10, getStrInMap(map_C_calc, "RSIGN_PEN_TECH_AMT"          )   ) + // C124ⓐ 【세액공제】연금계좌_과학기술인공제회법에따른퇴직연금_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_RSIGN_PEN_TECH_AMT"       )   ) + // C124ⓑ 【세액공제】연금계좌_과학기술인공제회법에따른퇴직연금_세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "RSIGN_PEN_RET_AMT"           )   ) + // C125ⓐ 【세액공제】연금계좌_근로자퇴직급여보장법에따른퇴직연금_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_RSIGN_PEN_RET_AMT"        )   ) + // C125ⓑ 【세액공제】연금계좌_근로자퇴직급여보장법에따른퇴직연금_세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "RSIGN_PEN_PF_AMT"            )   ) + // C126ⓐ 【세액공제】연금계좌_연금저축_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_RSIGN_PEN_PF_AMT"         )   ) + // C126ⓑ 【세액공제】연금계좌_연금저축_세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "ISA_PEN_AMT"                 )   ) + // C127ⓐ 【세액공제】ISA계좌만기시추가납입액_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_ISA_PEN_AMT"              )   ) + // C127ⓑ 【세액공제】ISA계좌만기시추가납입액_세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_IF_GRT_INSU_AMT"        )   ) + // C128ⓐ 【세액공제】특별세액공제_보장성보험료_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_IF_GRT_INSU_AMT"          )   ) + // C128ⓑ 【세액공제】특별세액공제_보장성보험료_세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_IF_HDC_PERS_INSU_AMT"   )   ) + // C129ⓐ 【세액공제】특별세액공제_장애인전용보장성보험료_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_IF_HDC_PERS_INSU_AMT"     )   ) + // C129ⓑ 【세액공제】특별세액공제_장애인전용보장성보험료_세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_MEDI_AMT"               )   ) + // C130ⓐ 【세액공제】특별세액공제_의료비_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_MEDI_AMT"                 )   ) + // C130ⓑ 【세액공제】특별세액공제_의료비_세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_EDU_AMT"                )   ) + // C131ⓐ 【세액공제】특별세액공제_교육비_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_EDU_AMT"                  )   ) + // C131ⓑ 【세액공제】특별세액공제_교육비_세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "SPCL_PF_AMT"                 )   ) + // C132ⓐ 【세액공제】㉮특별세액공제_기부금_정치자금_10만원이하_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_PF"                       )   ) + // C132ⓑ 【세액공제】㉮특별세액공제_기부금_정치자금_10만원이하_세액공제액
					makeStr("9",  11, getStrInMap(map_C_calc, "SPCL_POLITIC_FUND"           )   ) + // C133ⓐ 【세액공제】㉮특별세액공제_기부금_정치자금_10만원초과_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_POLITIC_FUND"             )   ) + // C133ⓑ 【세액공제】㉮특별세액공제_기부금_정치자금_10만원초과_세액공제액
					makeStr("9",  11, getStrInMap(map_C_calc, "SPCL_DON_LAW"                )   ) + // C134ⓐ 【세액공제】㉯소득세법제34조제2항제1호에따른기부금_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_DON_LAW"                  )   ) + // C134ⓑ 【세액공제】㉯소득세법제34조제2항제1호에따른기부금_세액공제액
					makeStr("9",  11, getStrInMap(map_C_calc, "SPCL_STOCK_URSM"             )   ) + // C135ⓐ 【세액공제】㉰특별세액공제_기부금_우리사주조합기부금_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_STOCK_URSM"               )   ) + // C135ⓑ 【세액공제】㉰특별세액공제_기부금_우리사주조합기부금_세액공제액
					makeStr("9",  11, getStrInMap(map_C_calc, "SPCL_PSA"                    )   ) + // C136ⓐ 【세액공제】㉱소득세법제34조제3항제1호의기부금(종교단체외)_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_PSA"                      )   ) + // C136ⓑ 【세액공제】㉱소득세법제34조제3항제1호의기부금(종교단체외)_세액공제액
					makeStr("9",  11, getStrInMap(map_C_calc, "SPCL_PSA_RELGN_AMT"          )   ) + // C137ⓐ 【세액공제】㉲소득세법제34조제3항제1호의기부금(종교단체)_공제대상금액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_PSA_RELGN"                )   ) + // C137ⓑ 【세액공제】㉲소득세법제34조제3항제1호의기부금(종교단체)_세액공제액
					makeStr("9",  11, "0"                                                       ) + // C138   【세액공제】공란
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_SPCL_SUM"                 )   ) + // C139   【세액공제】특별세액공제계
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_BASE_SUB_AMT"             )   ) + // C140   【세액공제】표준세액공제
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_PTU"                      )   ) + // C141   【세액공제】납세조합공제
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_HBA"                      )   ) + // C142   【세액공제】주택차입금
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_FCG"                      )   ) + // C143   【세액공제】외국납부
					makeStr("9",  10, getStrInMap(map_C_calc, "SP_HOUSE_RENT_AMT"           )   ) + // C144ⓐ 【세액공제】월세세액공제대상금액
					makeStr("9",   8, getStrInMap(map_C_calc, "RT_HOUSE_RENT_AMT"           )   ) + // C144ⓑ 【세액공제】월세세액공제액
					makeStr("9",  10, getStrInMap(map_C_calc, "RT_SUM"                      )   ) + // C145   【세액공제】세액공제계
					makeStr("9",  11, getStrInMap(map_C_calc, "RES_INCM_TAX"                )   ) + // C146ⓐ 【결정세액】소득세
					makeStr("9",  10, getStrInMap(map_C_calc, "RES_INHABT_TAX"              )   ) + // C146ⓑ 【결정세액】지방소득세
					makeStr("9",  10, getStrInMap(map_C_calc, "FST_DEC_TAX"                 )   ) + // C146ⓒ 【결정세액】농특세
					makeStr("9",   3, getStrInMap(map_C_calc, "EFFCTV_TAX_RATE"             )   ) + // C147   【결정세액】실효세율
					makeStr("9",  11, getStrInMap(map_C_main, "INCM_TAX"                    )   ) + // C148ⓐ 【기납부세액-주(현)근무지】소득세
					makeStr("9",  10, getStrInMap(map_C_main, "INHABT_TAX"                  )   ) + // C148ⓑ 【기납부세액-주(현)근무지】지방소득세
					makeStr("9",  10, getStrInMap(map_C_main, "FST_TAX"                     )   ) + // C148ⓒ 【기납부세액-주(현)근무지】농특세
					makeStr("9",  11, getStrInMap(map_C_calc, "PAY_SP_INCM_TAX"             )   ) + // C149ⓐ 【납부특례세액】소득세
					makeStr("9",  10, getStrInMap(map_C_calc, "PAY_SP_INHABT_TAX"           )   ) + // C149ⓑ 【납부특례세액】지방소득세
					makeStr("9",  10, getStrInMap(map_C_calc, "PAY_SP_FST_TAX"              )   ) + // C149ⓒ 【납부특례세액】농특세
					makeStr("9",   1, getStrInMap(map_C_calc, "SUB_INCM_SIGN"               )   ) + // C150ⓐ 【차감징수세액】소득세-부호
					makeStr("9",  11, getStrInMap(map_C_calc, "SUB_INCM_TAX"                )   ) + // C150ⓐ 【차감징수세액】소득세
					makeStr("9",   1, getStrInMap(map_C_calc, "SUB_INHABT_SIGN"             )   ) + // C150ⓑ 【차감징수세액】지방소득세-부호
					makeStr("9",  10, getStrInMap(map_C_calc, "SUB_INHABT_TAX"              )   ) + // C150ⓑ 【차감징수세액】지방소득세
					makeStr("9",   1, getStrInMap(map_C_calc, "SUB_FST_SIGN"                )   ) + // C150ⓒ 【차감징수세액】농특세-부호
					makeStr("9",  10, getStrInMap(map_C_calc, "SUB_FST_TAX"                 )   ) + // C150ⓒ 【차감징수세액】농특세
					makeStr("X", 128, " "                                                       ) + // C151   【차감징수세액】공란

				"\n"
			);

			check_wrk_map(CNTC,CALC_NO,NM,map_C_main);
			//check_wrk_map(CNTC,CALC_NO,NM,map_C_calc);








			//D레코드[종(전)근무처 레코드] ------------------------------------------------------------------------------------
			List list_D = getList("yts.main.Yts1050.getWRKListD", map_Emp);

			//sql문 오류가 아닌 이상 발생가능성 없음
			if(subCnt != list_D.size()){
				Map errList = (Map)getItem("yts.main.Yts1050.getErrList_RecordC", sessMap);
				msg = "파일생성 작업 중 오류가 발생하였습니다. \n\n" + NM + "(" + EMP_NO + ")"
					+ "\n상기 인력의 C-record의 종(전) 근무지와 D-record의 종(전)근무지 건수가 상이 합니다. \n연말정산팀에 문의하세요.";
				throw new Exception(msg);
			}

			recordSeq = 0;
			for (Iterator all_D = list_D.iterator(); all_D.hasNext();) {
				Map map_D = (Map) all_D.next();
				bw.write(
						makeStr("X",   1, "D"                                                       ) + // D1     【자료관리번호】레코드구분
						makeStr("9",   2, "20"                                                      ) + // D2     【자료관리번호】자료구분
						makeStr("X",   3, TAX_CD                                                    ) + // D3     【자료관리번호】세무서코드
						makeStr("9",   6, CNTC                                                      ) + // D4     【자료관리번호】일련번호
						makeStr("X",  10, BUSI_REG_NO                                               ) + // D5     【원천징수의무자】③사업자등록번호
						makeStr("X",  13, RES_NO                                                    ) + // D6     【소득자】⑦소득자주민등록번호
						makeStr("X",   1, getStrInMap(map_D, "REL_WRKR_YN"                      )   ) + // D7     【소득자】종교관련종사자여부
						makeStr("X",   1, getStrInMap(map_D, "SUB_CLS"                          )   ) + // D8     【근무처별소득명세-종(전)근무처】납세조합여부
						makeStr("X",  60, getStrInMap(map_D, "TRAD_NM"                          )   ) + // D9     【근무처별소득명세-종(전)근무처】⑨법인명(상호)
						makeStr("X",  10, getStrInMap(map_D, "BUSI_REG_NO"                      )   ) + // D10    【근무처별소득명세-종(전)근무처】⑩사업자등록번호
						makeStr("9",   8, getStrInMap(map_D, "ENT_DT"                           )   ) + // D11    【근무처별소득명세-종(전)근무처】⑪근무기간시작연월일
						makeStr("9",   8, getStrInMap(map_D, "RSIGN_DT"                         )   ) + // D12    【근무처별소득명세-종(전)근무처】⑪근무기간종료연월일
						makeStr("9",   8, getStrInMap(map_D, "CUT_TAX_FRM_DT"                   )   ) + // D13    【근무처별소득명세-종(전)근무처】⑫감면기간시작연월일
						makeStr("9",   8, getStrInMap(map_D, "CUT_TAX_TO_DT"                    )   ) + // D14    【근무처별소득명세-종(전)근무처】⑫감면기간종료연월일
						makeStr("9",  11, getStrInMap(map_D, "PAY_AMT"                          )   ) + // D15    【근무처별소득명세-종(전)근무처】⑬급여
						makeStr("9",  11, getStrInMap(map_D, "BNS_AMT"                          )   ) + // D16    【근무처별소득명세-종(전)근무처】⑭상여
						makeStr("9",  11, getStrInMap(map_D, "AGREE_BONUS_AMT"                  )   ) + // D17    【근무처별소득명세-종(전)근무처】⑮인정상여
						makeStr("9",  11, getStrInMap(map_D, "STOCK_OPT_BENEF"                  )   ) + // D18    【근무처별소득명세-종(전)근무처】⑮-1주식매수선택권행사이익
						makeStr("9",  11, getStrInMap(map_D, "STOCK_DRAW_ACCT"                  )   ) + // D19    【근무처별소득명세-종(전)근무처】⑮-2우리사주조합인출금
						makeStr("9",  11, getStrInMap(map_D, "EXCTV_RET_OVER"                   )   ) + // D20    【근무처별소득명세-종(전)근무처】⑮-3임원퇴직소득한도초과액
						makeStr("9",  11, getStrInMap(map_D, "DUTY_INVNT_RWD"                   )   ) + // D21    【근무처별소득명세-종(전)근무처】⑮-4직무발명보상금
						makeStr("9",  11, "0"                                                       ) + // D22    【근무처별소득명세-종(전)근무처】공란
						makeStr("9",  11, "0"                                                       ) + // D23    【근무처별소득명세-종(전)근무처】공란
						makeStr("9",  11, getStrInMap(map_D, "SUM_AMT"                          )   ) + // D24    【근무처별소득명세-종(전)근무처】계
						makeStr("9",  10, getStrInMap(map_D, "NTAX_G01"                         )   ) + // D25    【종(전)근무처비과세소득및감면소득】G01-비과세학자금

						makeStr("9",  10, getStrInMap(map_D, "NTAX_H05"                         )   ) + // D26    【종(전)근무처비과세소득및감면소득】H05-경호·승선수당
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H06"                         )   ) + // D27    【종(전)근무처비과세소득및감면소득】H06-유아·초중등
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H07"                         )   ) + // D28    【종(전)근무처비과세소득및감면소득】H07-고등교육법
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H08"                         )   ) + // D29    【종(전)근무처비과세소득및감면소득】H08-특별법
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H09"                         )   ) + // D30    【종(전)근무처비과세소득및감면소득】H09-연구기관등
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H10"                         )   ) + // D31    【종(전)근무처비과세소득및감면소득】H10-기업부설연구소
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H14"                         )   ) + // D32    【종(전)근무처비과세소득및감면소득】H14-보육교사근무환경개선비
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H15"                         )   ) + // D33    【종(전)근무처비과세소득및감면소득】H15-사립유치원수석교사･교사의인건비
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H11"                         )   ) + // D34    【종(전)근무처비과세소득및감면소득】H11-취재수당
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H12"                         )   ) + // D35    【종(전)근무처비과세소득및감면소득】H12-벽지수당
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H13"                         )   ) + // D36    【종(전)근무처비과세소득및감면소득】H13-재해관련급여
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H16"                         )   ) + // D37    【종(전)근무처비과세소득및감면소득】H16-정부‧공공기관지방이전기관종사자이주수당
						makeStr("9",  10, getStrInMap(map_D, "NTAX_H17"                         )   ) + // D38    【종(전)근무처비과세소득및감면소득】H17종교활동비
						makeStr("9",  10, getStrInMap(map_D, "NTAX_I01"                         )   ) + // D39    【종(전)근무처비과세소득및감면소득】I01-외국정부등근무자
						makeStr("9",  10, getStrInMap(map_D, "NTAX_K01"                         )   ) + // D40    【종(전)근무처비과세소득및감면소득】K01-외국주둔군인등
						makeStr("9",  10, getStrInMap(map_D, "NTAX_M01"                         )   ) + // D41    【종(전)근무처비과세소득및감면소득】M01-국외근로100만원
						makeStr("9",  10, getStrInMap(map_D, "NTAX_M02"                         )   ) + // D42    【종(전)근무처비과세소득및감면소득】M02-국외근로300만원
						makeStr("9",  10, getStrInMap(map_D, "NTAX_M03"                         )   ) + // D43    【종(전)근무처비과세소득및감면소득】M03-국외근로
						makeStr("9",  10, getStrInMap(map_D, "NTAX_O01"                         )   ) + // D44    【종(전)근무처비과세소득및감면소득】O01-야간근로수당
						makeStr("9",  10, getStrInMap(map_D, "NTAX_Q01"                         )   ) + // D45    【종(전)근무처비과세소득및감면소득】Q01-출산보육수당
						makeStr("9",  10, getStrInMap(map_D, "NTAX_R10"                         )   ) + // D46    【종(전)근무처비과세소득및감면소득】R10-근로장학금
						makeStr("9",  10, getStrInMap(map_D, "NTAX_R11"                         )   ) + // D47    【종(전)근무처비과세소득및감면소득】R11-직무발명보상금
						makeStr("9",  10, getStrInMap(map_D, "NTAX_S01"                         )   ) + // D48    【종(전)근무처비과세소득및감면소득】S01-주식매수선택권
						makeStr("9",  10, getStrInMap(map_D, "NTAX_U01"                         )   ) + // D49    【종(전)근무처비과세소득및감면소득】U01-벤처기업주식매수선택권
						makeStr("9",  10, getStrInMap(map_D, "NTAX_Y02"                         )   ) + // D50    【종(전)근무처비과세소득및감면소득】Y02-우리사주조합인출금50%
						makeStr("9",  10, getStrInMap(map_D, "NTAX_Y03"                         )   ) + // D51ⓐ  【종(전)근무처비과세소득및감면소득】Y03-우리사주조합인출금75%
						makeStr("9",  10, getStrInMap(map_D, "NTAX_Y04"                         )   ) + // D51ⓑ  【종(전)근무처비과세소득및감면소득】Y04-우리사주조합인출금100%
						makeStr("9",  10, getStrInMap(map_D, "NTAX_Y22"                         )   ) + // D51ⓒ  【종(전)근무처비과세소득및감면소득】Y22-전공의수련보조수당
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T01"                         )   ) + // D52ⓐ  【종(전)근무처비과세소득및감면소득】T01-외국인기술자소득세감면(50%)
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T02"                         )   ) + // D52ⓑ  【종(전)근무처비과세소득및감면소득】T02-외국인기술자소득세감면(70%)
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T30"                         )   ) + // D53    【종(전)근무처비과세소득및감면소득】T30-성과공유중소기업경영성과급
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T40"                         )   ) + // D54ⓐ  【종(전)근무처비과세소득및감면소득】T40-중소기업청년근로자및핵심인력성과보상기금수령액
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T41"                         )   ) + // D54ⓑ  【종(전)근무처비과세소득및감면소득】T41-중견기업청년근로자및핵심인력성과보상기금수령액
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T42"                         )   ) + // D54ⓒ  【종(전)근무처비과세소득및감면소득】T42-중견기업청년근로자및핵심인력성과보상기금소득세감면
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T43"                         )   ) + // D54ⓓ  【종(전)근무처비과세소득및감면소득】T43-중견기업청년근로자및핵심인력성과보상기금소득세감면
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T50"                         )   ) + // D55    【종(전)근무처비과세소득및감면소득】T50-내국인우수인력국내복귀소득세감면
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T11"                         )   ) + // D56ⓐ  【종(전)근무처비과세소득및감면소득】T11-중소기업취업자소득세감면50%
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T12"                         )   ) + // D56ⓑ  【종(전)근무처비과세소득및감면소득】T12-중소기업취업자소득세감면70%
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T13"                         )   ) + // D56ⓒ  【종(전)근무처비과세소득및감면소득】T13-중소기업취업자소득세감면90%
						makeStr("9",  10, getStrInMap(map_D, "NTAX_T20"                         )   ) + // D57    【종(전)근무처비과세소득및감면소득】T20-조세조약상교직자감면


						makeStr("9",  10, "0"                                                       ) + // D58    【종(전)근무처비과세소득및감면소득】공란
						makeStr("9",  10, getStrInMap(map_D, "NTAX_SUM"                         )   ) + // D59    【종(전)근무처비과세소득및감면소득】비과세계
						makeStr("9",  10, getStrInMap(map_D, "CUT_TAX_SUM"                      )   ) + // D60    【종(전)근무처비과세소득및감면소득】감면소득계
						makeStr("9",  11, getStrInMap(map_D, "INCM_TAX"                         )   ) + // D61ⓐ  【기납부세액-종(전)근무지】소득세
						makeStr("9",  10, getStrInMap(map_D, "INHABT_TAX"                       )   ) + // D62ⓑ  【기납부세액-종(전)근무지】지방소득세
						makeStr("9",  10, getStrInMap(map_D, "FST_TAX"                          )   ) + // D63ⓒ  【기납부세액-종(전)근무지】농특세
						makeStr("9",   2, String.valueOf( ++recordSeq )                             ) + // D64    【기납부세액-종(전)근무지】종(전)근무처일련번호
						makeStr("X",1288, " "                                                       ) + // D65    【기납부세액-종(전)근무지】공란

					"\n"
				);

				//check_wrk_map(CNTC,CALC_NO,NM,map_D);

			}

			//E레코드[부양가족공제자 명세 레코드] ------------------------------------------------------------------------------------
			List list_E = getList("yts.main.Yts1050.getWRKListE", map_Emp);
			Map map_E   = null;

			recordSeq      = 0;
			realCount      = list_E.size();
			countPerRecord = 3; //한 레코드에 3명씩 표기
			totalCount     = (int)Math.ceil( realCount / (double)countPerRecord ) * countPerRecord;

			for (int i = 0; i < totalCount; i++) {
				if ( i < realCount ) map_E = (Map)list_E.get(i);

				if ( i % countPerRecord == 0 ) {
					bw.write(
							makeStr("X",   1, "E"                                                       ) + // E1     【자료관리번호】레코드구분
							makeStr("9",   2, "20"                                                      ) + // E2     【자료관리번호】자료구분
							makeStr("X",   3, TAX_CD                                                    ) + // E3     【자료관리번호】세무서코드
							makeStr("9",   6, CNTC                                                      ) + // E4     【자료관리번호】일련번호
							makeStr("X",  10, BUSI_REG_NO                                               ) + // E5     【원천징수의무자】③사업자등록번호
							makeStr("X",  13, RES_NO                                                    ) + // E6     【소득자】⑦소득자주민등록번호

						""
					);
				}
				if ( i < realCount ) {
					bw.write(
							makeStr("X",   1, getStrInMap(map_E, "FMLY_RELN_WRK"                    )   ) + // E7     【소득공제명세의인적사항1】관계
							makeStr("X",   1, getStrInMap(map_E, "HOME_CLS"                         )   ) + // E8     【소득공제명세의인적사항1】내･외국인구분코드
							makeStr("X",  30, getStrInMap(map_E, "NM"                               )   ) + // E9     【소득공제명세의인적사항1】성명
							makeStr("X",  13, getStrInMap(map_E, "RES_NO"                           )   ) + // E10    【소득공제명세의인적사항1】주민등록번호
							makeStr("X",   1, getStrInMap(map_E, "BAS_SUB_YN"                       )   ) + // E11    【소득공제명세의인적사항1】기본공제
							makeStr("X",   1, getStrInMap(map_E, "HDC_PERS_YN"                      )   ) + // E12    【소득공제명세의인적사항1】장애인공제
							makeStr("X",   1, getStrInMap(map_E, "LADY_YN"                          )   ) + // E13    【소득공제명세의인적사항1】부녀자공제
							makeStr("X",   1, getStrInMap(map_E, "OB_TRE_YN"                        )   ) + // E14    【소득공제명세의인적사항1】경로우대공제
							makeStr("X",   1, getStrInMap(map_E, "SNGL_PRNT_YN"                     )   ) + // E15    【소득공제명세의인적사항1】한부모가족공제
							makeStr("X",   1, getStrInMap(map_E, "PER_CHI_YN"                       )   ) + // E16    【소득공제명세의인적사항1】출산‧입양공제
							makeStr("X",   1, getStrInMap(map_E, "CHILD_YN"                         )   ) + // E17    【소득공제명세의인적사항1】자녀공제
							makeStr("X",   1, getStrInMap(map_E, "EDU_CLS"                          )   ) + // E18    【소득공제명세의인적사항1】교육비공제
							makeStr("9",  10, getStrInMap(map_E, "HLTH_INSU_1"                      )   ) + // E19    【소득공제명세의국세청자료1】보험료_건강보험
							makeStr("9",  10, getStrInMap(map_E, "EMP_INSU_1"                       )   ) + // E20    【소득공제명세의국세청자료1】보험료_고용보험
							makeStr("9",  10, getStrInMap(map_E, "GRT_INSU_1"                       )   ) + // E21    【소득공제명세의국세청자료1】보험료_보장성보험
							makeStr("9",  10, getStrInMap(map_E, "HDC_PERS_INSU_1"                  )   ) + // E22    【소득공제명세의국세청자료1】보험료_장애인전용보장성보험
							makeStr("9",  10, getStrInMap(map_E, "MEDI_AMT_1"                       )   ) + // E23    【소득공제명세의국세청자료1】의료비_일반
							makeStr("9",  10, getStrInMap(map_E, "MEDI_CA_AMT_1"                    )   ) + // E24    【소득공제명세의국세청자료1】의료비_미숙아‧선천성이상아
							makeStr("9",  10, getStrInMap(map_E, "MEDI_ISA_AMT_1"                   )   ) + // E25    【소득공제명세의국세청자료1】의료비_난임
							makeStr("9",  10, getStrInMap(map_E, "MEDI_HDC_MC_AMT_1"                )   ) + // E26    【소득공제명세의국세청자료1】의료비_65세이상‧장애인‧건강보험산정특례자
							makeStr("9",  10, getStrInMap(map_E, "MEDI_LOSS_INSU_1"                 )   ) + // E27    【소득공제명세의국세청자료1】의료비_실손의료보험금
							makeStr("9",  10, getStrInMap(map_E, "EDU_AMT_1"                        )   ) + // E28    【소득공제명세의국세청자료1】교육비_일반
							makeStr("9",  10, getStrInMap(map_E, "EDU_HDC_PERS_AMT_1"               )   ) + // E29    【소득공제명세의국세청자료1】교육비_장애인특수교육비
							makeStr("9",  10, getStrInMap(map_E, "DIR_CARD_1"                       )   ) + // E30    【소득공제명세의국세청자료1】신용카드
							makeStr("9",  10, getStrInMap(map_E, "DIR_PRPD_CARD_1"                  )   ) + // E31    【소득공제명세의국세청자료1】직불카드등
							makeStr("9",  10, getStrInMap(map_E, "CASH_RECPT_1"                     )   ) + // E32    【소득공제명세의국세청자료1】현금영수증
							makeStr("9",  10, getStrInMap(map_E, "BOOK_PFMNC_1"                     )   ) + // E33    【소득공제명세의국세청자료1】도서‧공연등사용분
							makeStr("9",  10, getStrInMap(map_E, "TRD_MARKET_1"                     )   ) + // E34    【소득공제명세의국세청자료1】전통시장사용분

							makeStr("9",  10, getStrInMap(map_E, "PUBL_TRNSP_FH_1"                  )   ) + // E35    【소득공제명세의국세청자료1】대중교통이용분_1-6월
							makeStr("9",  10, getStrInMap(map_E, "PUBL_TRNSP_SH_1"                  )   ) + // E36    【소득공제명세의국세청자료1】대중교통이용분_7-12월
							makeStr("9",  10, getStrInMap(map_E, "PRE_YY_CARD_SUM_1"                )   ) + // E37    【소득공제명세의국세청자료1】소비증가분_2021년전체사용분
							makeStr("9",  10, getStrInMap(map_E, "PRE_YY_TRD_MARKET_1"              )   ) + // E38    【소득공제명세의국세청자료1】소비증가분_2021년전통시장사용분
							makeStr("9",  10, getStrInMap(map_E, "CUR_YY_CARD_SUM_1"                )   ) + // E39    【소득공제명세의국세청자료1】소비증가분_2022년전체사용분
							makeStr("9",  10, getStrInMap(map_E, "CUR_YY_TRD_MARKET_1"              )   ) + // E40    【소득공제명세의국세청자료1】소비증가분_2022년전통시장사용분
							makeStr("9",  13, getStrInMap(map_E, "GIFT_AMT_1"                       )   ) + // E41    【소득공제명세의국세청자료1】기부금

							makeStr("9",  10, getStrInMap(map_E, "HLTH_INSU_2"                      )   ) + // E42    【소득공제명세의기타자료1】보험료_건강보험
							makeStr("9",  10, getStrInMap(map_E, "EMP_INSU_2"                       )   ) + // E43    【소득공제명세의기타자료1】보험료_고용보험
							makeStr("9",  10, getStrInMap(map_E, "GRT_INSU_2"                       )   ) + // E44    【소득공제명세의기타자료1】보험료_보장성보험
							makeStr("9",  10, getStrInMap(map_E, "HDC_PERS_INSU_2"                  )   ) + // E45    【소득공제명세의기타자료1】보험료_장애인전용보장성보험
							makeStr("9",  10, getStrInMap(map_E, "MEDI_AMT_2"                       )   ) + // E46    【소득공제명세의기타자료1】의료비_일반
							makeStr("9",  10, getStrInMap(map_E, "MEDI_CA_AMT_2"                    )   ) + // E47    【소득공제명세의기타자료1】의료비_미숙아‧선천성이상아
							makeStr("9",  10, getStrInMap(map_E, "MEDI_ISA_AMT_2"                   )   ) + // E48    【소득공제명세의기타자료1】의료비_난임
							makeStr("9",  10, getStrInMap(map_E, "MEDI_HDC_MC_AMT_2"                )   ) + // E49    【소득공제명세의기타자료1】의료비_65세이상‧장애인‧건강보험산정특례자
							makeStr("9",   1, getStrInMap(map_E, "MEDI_LOSS_INSU_2_SIGN"            )   ) + // E50    【소득공제명세의기타자료1】의료비_실손의료보험금-부호
							makeStr("9",  10, getStrInMap(map_E, "MEDI_LOSS_INSU_2"                 )   ) + // E50    【소득공제명세의기타자료1】의료비_실손의료보험금
							makeStr("9",  10, getStrInMap(map_E, "EDU_AMT_2"                        )   ) + // E51    【소득공제명세의기타자료1】교육비_일반
							makeStr("9",  10, getStrInMap(map_E, "EDU_HDC_PERS_AMT_2"               )   ) + // E52    【소득공제명세의기타자료1】교육비_장애인특수교육
							makeStr("9",  10, getStrInMap(map_E, "DIR_CARD_2"                       )   ) + // E53    【소득공제명세의기타자료1】신용카드
							makeStr("9",  10, getStrInMap(map_E, "DIR_PRPD_CARD_2"                  )   ) + // E54    【소득공제명세의기타자료1】직불카드등
							makeStr("9",  10, getStrInMap(map_E, "BOOK_PFMNC_2"                     )   ) + // E55    【소득공제명세의기타자료1】도서‧공연등사용분
							makeStr("9",  10, getStrInMap(map_E, "TRD_MARKET_2"                     )   ) + // E56    【소득공제명세의기타자료1】전통시장사용분

							makeStr("9",  10, getStrInMap(map_E, "PUBL_TRNSP_FH_2"                  )   ) + // E57    【소득공제명세의기타자료1】대중교통이용분_1-6월
							makeStr("9",  10, getStrInMap(map_E, "PUBL_TRNSP_SH_2"                  )   ) + // E58    【소득공제명세의기타자료1】대중교통이용분_7-12월
							makeStr("9",  10, getStrInMap(map_E, "PRE_YY_CARD_SUM_2"                )   ) + // E59    【소득공제명세의기타자료1】소비증가분_2021년전체사용분
							makeStr("9",  10, getStrInMap(map_E, "PRE_YY_TRD_MARKET_2"              )   ) + // E60    【소득공제명세의기타자료1】소비증가분_2021년전통시장사용분
							makeStr("9",  10, getStrInMap(map_E, "CUR_YY_CARD_SUM_2"                )   ) + // E61    【소득공제명세의기타자료1】소비증가분_2022년전체사용분
							makeStr("9",  10, getStrInMap(map_E, "CUR_YY_TRD_MARKET_2"              )   ) + // E62    【소득공제명세의기타자료1】소비증가분_2022년전통시장사용분
							makeStr("9",  13, getStrInMap(map_E, "GIFT_AMT_2"                       )   ) + // E63    【소득공제명세의기타자료1】기부금

							""
					);
				} else {
					bw.write(
							makeStr("X",  53, " "                                                       ) + // 【반복】
							makeStr("9", 457, "0"                                                       ) + // 【반복】
							""
					);
				}

				if ( (i + 1) % countPerRecord == 0 ) {
					bw.write(
							makeStr("9",   2, String.valueOf( ++recordSeq )                             ) + // E178   【소득공제명세의기타자료3】부양가족레코드일련번호
							makeStr("X", 443, " "                                                       ) + // E179   【소득공제명세의기타자료3】공란

							"\n"
					);
				}

				//if( i < realCount ) { check_wrk_map(CNTC,CALC_NO,NM,map_E); }

			}

			//F레코드[연금저축 등 소득.세액 공제명세 레코드] ------------------------------------------------------------------------------------
			List list_F = getList("yts.main.Yts1050.getWRKListF", map_Emp);
			Map map_F   = null;

			recordSeq      = 0;
			realCount      = list_F.size();
			countPerRecord = 15; // 한 레코드에 15건씩 표기
			totalCount = (int)Math.ceil( realCount / (double)countPerRecord ) * countPerRecord;

			for (int i = 0; i < totalCount; i++) {
				if ( i < realCount ) map_F = (Map)list_F.get(i);

				if ( i % countPerRecord == 0 ) {
					bw.write(
							makeStr("X",   1, "F"                                                       ) + // F1     【자료관리번호】레코드구분
							makeStr("9",   2, "20"                                                      ) + // F2     【자료관리번호】자료구분
							makeStr("X",   3, TAX_CD                                                    ) + // F3     【자료관리번호】세무서코드
							makeStr("9",   6, CNTC                                                      ) + // F4     【자료관리번호】일련번호
							makeStr("X",  10, BUSI_REG_NO                                               ) + // F5     【원천징수의무자】②사업자등록번호
							makeStr("X",  13, RES_NO                                                    ) + // F6     【소득자】④소득자주민등록번호

						""
					);
	 			}

				if ( i < realCount ) {
					bw.write(

							makeStr("X",   2, getStrInMap(map_F, "PEN_SAVE_CLS"                     )   ) + // F7     【연금･저축등소득·세액공제명세1】소득공제구분
							makeStr("X",   3, getStrInMap(map_F, "FNC_ORG_CD"                       )   ) + // F8     【연금･저축등소득·세액공제명세1】금융기관코드
							makeStr("X",  60, getStrInMap(map_F, "FNC_ORG_NM"                       )   ) + // F9     【연금･저축등소득·세액공제명세1】금융기관상호
							makeStr("X",  20, getStrInMap(map_F, "ACC_NO"                           )   ) + // F10    【연금･저축등소득·세액공제명세1】계좌번호(또는증권번호)
							makeStr("9",  10, getStrInMap(map_F, "PEN_SAVE_PMT_AMT"                 )   ) + // F11    【연금･저축등소득·세액공제명세1】납입금액
							makeStr("9",  10, getStrInMap(map_F, "PEN_SAVE_SUB_AMT"                 )   ) + // F12    【연금･저축등소득·세액공제명세1】소득·세액공제금액
							makeStr("9",   4, getStrInMap(map_F, "INVST_YY"                         )   ) + // F13    【연금･저축등소득·세액공제명세1】투자연도
							makeStr("X",   1, getStrInMap(map_F, "INVST_CLS"                        )   ) + // F14    【연금･저축등소득·세액공제명세1】투자구분
							makeStr("9",   8, getStrInMap(map_F, "REG_DT"                           )   ) + // F15    【연금･저축등소득·세액공제명세1】가입일
							makeStr("9",   2, getStrInMap(map_F, "CTRCT_PRD"                        )   ) + // F16    【연금･저축등소득·세액공제명세1】계약기간


							""
					);
				} else {
					bw.write(
							makeStr("X",  85, " "                                                       ) + // 【반복】
							makeStr("9",  24, "0"                                                       ) + // 【반복】
							makeStr("X",   1, " "                                                       ) + // 【반복】
							makeStr("9",  10, "0"                                                       ) + // 【반복】
							""
					);
				}

				if ( (i + 1) % countPerRecord == 0 ) {
					bw.write(
							makeStr("9",   2, String.valueOf( ++recordSeq )                             ) + // F157   【연금･저축등소득·세액공제명세15】연금․저축레코드일련번호
							makeStr("X", 173, " "                                                       ) + // F158   【연금･저축등소득·세액공제명세15】공란
							"\n"
					);
				}

				//if ( i < realCount ) { check_wrk_map(CNTC,CALC_NO,NM,map_F); }

			}

			//G레코드[월세액.거주자간 주택임차차입금 원리금 상환액 소득공제명세] ------------------------------------------------------------------------------------
			List list_G    = getList("yts.main.Yts1050.getWRKListG", map_Emp);
			recordSeq      = 0;

			realCount      = list_G.size();
			countPerRecord = 3; // 한 레코드에 3건씩 표기
			totalCount     = (int)Math.ceil( realCount / (double)countPerRecord ) * countPerRecord;

			for (int i = 0; i < totalCount; i++) {
				Map map_G      = null;
				if ( i < realCount ) map_G = (Map)list_G.get(i);

				if ( i % countPerRecord == 0 ) {
					bw.write(
							makeStr("X",   1, "G"                                                       ) + // G1     【자료관리번호】레코드구분
							makeStr("9",   2, "20"                                                      ) + // G2     【자료관리번호】자료구분
							makeStr("X",   3, TAX_CD                                                    ) + // G3     【자료관리번호】세무서코드
							makeStr("9",   6, CNTC                                                      ) + // G4     【자료관리번호】일련번호
							makeStr("X",  10, BUSI_REG_NO                                               ) + // G5     【원천징수의무자】사업자등록번호
							makeStr("X",  13, RES_NO                                                    ) + // G6     【소득자】소득자주민등록번호
							makeStr("X",   2, "01"                                                      ) + // G7     【소득자】무주택자해당여부

						""
					);
				}

				if ( i < realCount ) {
					bw.write(
							makeStr("X",  60, getStrInMap(map_G, "A_RENT_AGNT_NM"                   )   ) + // G8     【월세액세액공제명세1】임대인성명(상호)
							makeStr("X",  13, getStrInMap(map_G, "A_RES_NO"                         )   ) + // G9     【월세액세액공제명세1】주민등록번호(사업자등록번호)
							makeStr("X",   1, getStrInMap(map_G, "A_HOUSE_TP"                       )   ) + // G10    【월세액세액공제명세1】유형
							makeStr("9",   5, getStrInMap(map_G, "A_HOUSE_DIM"                      )   ) + // G11    【월세액세액공제명세1】(월세)계약면적(㎡)
							makeStr("X", 150, getStrInMap(map_G, "A_RENT_CNTRCT_ADDR"               )   ) + // G12    【월세액세액공제명세1】임대차계약서상주소지
							makeStr("9",   8, getStrInMap(map_G, "A_CNTRCT_FRM_DT"                  )   ) + // G13    【월세액세액공제명세1】임대차계약기간개시일
							makeStr("9",   8, getStrInMap(map_G, "A_CNTRCT_TO_DT"                   )   ) + // G14    【월세액세액공제명세1】임대차계약기간종료일
							makeStr("9",  10, getStrInMap(map_G, "A_HOUSE_RENT"                     )   ) + // G15    【월세액세액공제명세1】연간월세액(원)
							makeStr("9",  10, getStrInMap(map_G, "A_RENT_HABT_SUB_AMT"              )   ) + // G16    【월세액세액공제명세1】세액공제금액(원)
							makeStr("X",  60, getStrInMap(map_G, "B_RENT_AGNT_NM"                   )   ) + // G17    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】대주(貸主)성명
							makeStr("X",  13, getStrInMap(map_G, "B_RES_NO"                         )   ) + // G18    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】대주주민등록번호
							makeStr("9",   8, getStrInMap(map_G, "B_CNTRCT_FRM_DT"                  )   ) + // G19    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】금전소비대차계약기간개시일
							makeStr("9",   8, getStrInMap(map_G, "B_CNTRCT_TO_DT"                   )   ) + // G20    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】금전소비대차계약기간종료일
							makeStr("9",   4, getStrInMap(map_G, "B_BRRW_INTR_RAT"                  )   ) + // G21    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】차입금이자율
							makeStr("9",  10, getStrInMap(map_G, "B_PNINT_SUM"                      )   ) + // G22    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】원리금상환액계
							makeStr("9",  10, getStrInMap(map_G, "B_PNINT_PRNCPAL"                  )   ) + // G23    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】원금
							makeStr("9",  10, getStrInMap(map_G, "B_PNINT_INTR"                     )   ) + // G24    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】이자
							makeStr("9",  10, getStrInMap(map_G, "B_RENT_HABT_SUB_AMT"              )   ) + // G25    【거주자간주택임차차입금원리금상환액소득공제명세-금전소비대차계약내용1】공제금액
							makeStr("X",  60, getStrInMap(map_G, "C_RENT_AGNT_NM"                   )   ) + // G26    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용1】임대인성명(상호)
							makeStr("X",  13, getStrInMap(map_G, "C_RES_NO"                         )   ) + // G27    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용1】주민등록번호(사업자등록번호)
							makeStr("X",   1, getStrInMap(map_G, "C_HOUSE_TP"                       )   ) + // G28    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용1】유형
							makeStr("9",   5, getStrInMap(map_G, "C_HOUSE_DIM"                      )   ) + // G29    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용1】(임대차)계약면적(㎡)
							makeStr("X", 150, getStrInMap(map_G, "C_RENT_CNTRCT_ADDR"               )   ) + // G30    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용1】임대차계약서상주소지
							makeStr("9",   8, getStrInMap(map_G, "C_CNTRCT_FRM_DT"                  )   ) + // G31    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용1】임대차계약기간개시일
							makeStr("9",   8, getStrInMap(map_G, "C_CNTRCT_TO_DT"                   )   ) + // G32    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용1】임대차계약기간종료일
							makeStr("9",  10, getStrInMap(map_G, "C_LFSTS_GRNTY_AMT"                )   ) + // G33    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용1】전세보증금(원)

						""
					);
				} else { //빈값 세팅
					bw.write(
						makeStr("X",  74, " "                                                       ) + //
						makeStr("9",   5, "0"                                                       ) + //
						makeStr("X", 150, " "                                                       ) + //
						makeStr("9",  36, "0"                                                       ) + //
						makeStr("X",  73, " "                                                       ) + //
						makeStr("9",  60, "0"                                                       ) + //
						makeStr("X",  74, " "                                                       ) + //
						makeStr("9",   5, "0"                                                       ) + //
						makeStr("X", 150, " "                                                       ) + //
						makeStr("9",  26, "0"                                                       ) + //
						""
					);
				}

				if ( (i + 1) % countPerRecord == 0 ) {
					bw.write(
							makeStr("9",   2, String.valueOf( ++recordSeq )                             ) + // G86    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용3】월세액･거주자간주택임차차입금레코드일련번호
							makeStr("X",  12, " "                                                       ) + // G87    【거주자간주택임차차입금원리금상환액소득공제명세-임대차계약내용3】공란

						"\n"
					);
				}

				//if ( i < realCount ) { check_wrk_map(CNTC,CALC_NO,NM,map_G); }

			}

			//H레코드[기부금 조정명세 레코드] ------------------------------------------------------------------------------------
			List list_H = getList("yts.main.Yts1050.getWRKListH", map_Emp);
			recordSeq   = 0;
			for (Iterator all_H = list_H.iterator(); all_H.hasNext();)	{
				Map map_H = (Map) all_H.next();
				bw.write(
						makeStr("X",   1, "H"                                                       ) + // H1     【자료관리번호】레코드구분
						makeStr("9",   2, "20"                                                      ) + // H2     【자료관리번호】자료구분
						makeStr("X",   3, TAX_CD                                                    ) + // H3     【자료관리번호】세무서
						makeStr("9",   6, CNTC                                                      ) + // H4     【자료관리번호】소득자일련번호
						makeStr("X",  10, BUSI_REG_NO                                               ) + // H5     【원천징수의무자】②사업자등록번호
						makeStr("X",  13, RES_NO                                                    ) + // H6     【소득자(연말정산신청자)】④주민등록번호
						makeStr("9",   1, HOME_CLS                                                  ) + // H7     【소득자(연말정산신청자)】내･외국인구분코드
						makeStr("X",  30, NM                                                        ) + // H8     【소득자(연말정산신청자)】③성명
						makeStr("X",   2, getStrInMap(map_H, "GIFT_CLS"                         )   ) + // H9     【기부금조정명세】코드
						makeStr("9",   4, getStrInMap(map_H, "GIFT_YY"                          )   ) + // H10    【기부금조정명세】기부연도
						makeStr("9",  13, getStrInMap(map_H, "GIFT_AMT"                         )   ) + // H11    【기부금조정명세】기부금액
						makeStr("9",  13, getStrInMap(map_H, "PREV_YY_SUB_AMT"                  )   ) + // H12    【기부금조정명세】⑰전년까지공제된금액
						makeStr("9",  13, getStrInMap(map_H, "GIFT_ABLE_SUB_AMT"                )   ) + // H13    【기부금조정명세】⑱공제대상금액(⑯-⑰)
						makeStr("9",  13, "0"                                                       ) + // H14    【기부금조정명세】해당연도공제금액필요경비
						makeStr("9",  13, getStrInMap(map_H, "CUR_YY_SUB_AMT"                   )   ) + // H15    【기부금조정명세】해당연도공제금액_세액(소득)공제
						makeStr("9",  13, getStrInMap(map_H, "CUR_YY_NON_EXPR_AMT"              )   ) + // H16    【기부금조정명세】해당연도에공제받지못한금액_소멸금액
						makeStr("9",  13, getStrInMap(map_H, "CUR_YY_NON_CARF_AMT"              )   ) + // H17    【기부금조정명세】해당연도에공제받지못한금액_이월금액
						makeStr("9",   5, String.valueOf( ++recordSeq )                             ) + // H18    【기부금조정명세】기부금조정명세일련번호
						makeStr("X",1842, " "                                                       ) + // H19    【기부금조정명세】공란

					"\n"
				);

				//check_wrk_map(CNTC,CALC_NO,NM,map_H);

			}

			//I레코드[해당 연도 기부명세 레코드] ------------------------------------------------------------------------------------
			List list_I = getList("yts.main.Yts1050.getWRKListI", map_Emp);
			recordSeq   = 0;
			for (Iterator all_I = list_I.iterator(); all_I.hasNext();){
				Map map_I = (Map) all_I.next();
				bw.write(
						makeStr("X",   1, "I"                                                       ) + // I1     【자료관리번호】레코드구분
						makeStr("9",   2, "20"                                                      ) + // I2     【자료관리번호】자료구분
						makeStr("X",   3, TAX_CD                                                    ) + // I3     【자료관리번호】세무서코드
						makeStr("9",   6, CNTC                                                      ) + // I4     【자료관리번호】소득자일련번호
						makeStr("X",  10, BUSI_REG_NO                                               ) + // I5     【원천징수의무자】②사업자등록번호
						makeStr("X",  13, RES_NO                                                    ) + // I6     【소득자(연말정산신청자)】④소득자주민등록번호
						makeStr("X",   2, getStrInMap(map_I, "GIFT_CLS"                         )   ) + // I7     【기부유형코드】⑦코드
						makeStr("X",   1, getStrInMap(map_I, "AMT_CLS"                          )   ) + // I8     【기부유형코드】⑧기부내용
						makeStr("X",  13, getStrInMap(map_I, "GIFT_BUSI_REG_NO"                 )   ) + // I9     【기부처】⑩사업자(주민)등록번호
						makeStr("X",  60, getStrInMap(map_I, "GIFT_COMP"                        )   ) + // I10    【기부처】⑨상호(법인명)
						makeStr("X",   1, getStrInMap(map_I, "FMLY_RELN"                        )   ) + // I11    【기부자】⑪관계코드
						makeStr("X",   1, getStrInMap(map_I, "FMLY_HOME_CLS"                    )   ) + // I12    【기부자】⑪내･외국인구분코드
						makeStr("X",  30, getStrInMap(map_I, "FMLY_NM"                          )   ) + // I13    【기부자】⑪성명
						makeStr("X",  13, getStrInMap(map_I, "FMLY_RES_NO"                      )   ) + // I14    【기부자】⑪주민등록번호
						makeStr("9",   5, getStrInMap(map_I, "GIFT_CNT"                         )   ) + // I15    【기부명세】건수
						makeStr("9",  13, getStrInMap(map_I, "GIFT_TOT_AMT"                     )   ) + // I16    【기부명세】⑫기부금합계금액(⑬+⑭)
						makeStr("9",  13, getStrInMap(map_I, "AMT"                              )   ) + // I17    【기부명세】⑬공제대상기부금액
						makeStr("9",  13, getStrInMap(map_I, "GIFT_ENC_RQST_AMT"                )   ) + // I18    【기부명세】⑭기부장려금신청금액
						makeStr("9",  13, "0"                                                       ) + // I19    【기부명세】⑮기타
						makeStr("9",   5, String.valueOf( ++recordSeq )                             ) + // I20    【기부명세】해당연도기부명세일련번호
						makeStr("X",1792, " "                                                       ) + // I21    【기부명세】공란

					"\n"
				);

				//check_wrk_map(CNTC,CALC_NO,NM,map_I);

			}
		}



		bw.close();




		//화면의 확정인원  (empYCnt)과   c-record의 수               (C_mainCnt)  가 일치해야함
		//화면의 총급여    (totPayAmt)와 c-record의 급여의 총합계    (C_totPayAmt)가 일치해야함
		//화면의 총결정세액(taxSum)과    c-record의 결정세액의 총합계(C_taxSum)   가 일치해야함
		//b-record의 종근무지건수(B_subCnt)와 c-record의 종근무지건수(C_subCnt)가 일치해야함

		//내용검증 CHECK -----------------------------------------------------------------------------------------------------------------------------
		System.out.println("\n\n>>파일 생성이 완료되었습니다.");
		System.out.println("\n\n>>화면(screen) 조회 금액과 파일 내 금액 비교=========================================================\n");
		System.out.println(String.format("소득자수   %,15d screen   %,15d C-record  %,15d Gap", empYCnt		,C_mainCnt		,empYCnt	- C_mainCnt));
		System.out.println(String.format("총급여     %,15d screen   %,15d C-record  %,15d Gap", totPayAmt	,C_totPayAmt	,totPayAmt	- C_totPayAmt));
		System.out.println(String.format("결정세액   %,15d screen   %,15d C-record  %,15d Gap", taxSum		,C_taxSum		,taxSum		- C_taxSum));
		System.out.println(String.format("전근무지수 %,15d B-record %,15d C-record  %,15d Gap", B_subCnt	,C_subCnt		,B_subCnt	- C_subCnt));
		System.out.println("");

		msg = "";
		if((empYCnt   != C_mainCnt)) 						{ msg += "*화면의 확정인원수와 파일(C)의 확정인원수가 상이합니다.\n";				}
		if((totPayAmt != C_totPayAmt || taxSum != C_taxSum)){ msg += "*화면의 총급여/결정세액과 파일(C)의 총급여/결정새액과 상이합니다.\n";		}
		if((B_subCnt  != C_subCnt)) 						{ msg += "*B레코드 종(전)근무지수와 C레코드 종(전)근무지수가 상이합니다.\n";		}
		if(!msg.equals("")) 								{ msg = "\n\n" + msg + "\n상기 오류를 조치하신 후 파일 재생성 바랍니다. \n조치 방법은 연말정산팀에 문의하세요."; }




		return write(mode+"▥"+cmd+"▥"+fileNm+"▥"+msg);

	}





	//---------------------------------------------------------------------------------------------------------------
	// 의료비명세 파일 생성
	//---------------------------------------------------------------------------------------------------------------

	private IResponse getMediFile(IRequest request) throws Exception{
		Map sessMap =  getLoginMap(request);
		String mode = (String)sessMap.get("mode");
		String cmd  = (String)sessMap.get("cmd");
		String withhldResper = (String)sessMap.get("WITHHLD_RESPER");

		//파일오픈----------------------------------------------------------------------------------------------------------
		Map resMap	  = (Map)getItem("yts.main.Yts1050.getfilenm", sessMap);
		//실제 오류발생 가능성은 없음
		if(resMap == null) { throw new Exception("파일생성 작업 중 오류가 발생하였습니다. \n\n파일정보를 찾을 수 없습니다.\n(원천징수의무자코드: "+ withhldResper +")"); }
		String fileNm = (String)resMap.get("FILENAME");
		Writer bw	  = openFile(fileNm);

		String msg 		   = "";
	    String strSubmitDt = ((String)sessMap.get("SUBMIT_DT")).replace("-","");
		String strIncYy    =  (String)sessMap.get("YY");

		sessMap.put("Dir", "Dir"); 	//건기연 공통변경으로 인하여 변수처리

		//사업장정보----------------------------------------------------------------------------------------------------------
		List wrk_list =	getList("yts.main.Yts1050.wrkCorp", sessMap);
		if(wrk_list.size() != 1) { throw new Exception("원천징수의무자 정보를 찾을 수 없습니다.\n(원천징수의무자코드: "+ withhldResper +")"); }
		Map cMap = (Map)wrk_list.get(0);

		String TAX_CD      	= (String)cMap.get("TAX_CD");
		String BUSI_REG_NO 	= (String)cMap.get("BUSI_REG_NO");
		String HOMETAX_ID   = (String)cMap.get("HOMETAX_ID");
		String CORP_NM		= (String)cMap.get("CORP_NM");

		List list =	getList("yts.main.Yts1050.getMediList", sessMap);
		msg = Integer.toString(list.size());
		int cnt	  = 0;
		for (Iterator all = list.iterator(); all.hasNext();){

			//if(++cnt>100) break;

			Map map_M = (Map) all.next();
			bw.write(
					makeStr("X",   1, "A"                                                       ) + // 1      【자료관리번호】레코드구분
					makeStr("9",   2, "26"                                                      ) + // 2      【자료관리번호】자료구분
					makeStr("X",   3, TAX_CD                                                    ) + // 3      【자료관리번호】세무서코드
					makeStr("9",   6, getStrInMap(map_M, "CNT2"                             )   ) + // 4      【자료관리번호】일련번호
					makeStr("9",   8, strSubmitDt                                               ) + // 5      【자료관리번호】제출년월일
					makeStr("X",  10, BUSI_REG_NO                                               ) + // 6      【제출자】사업자등록번호
					makeStr("X",  20, HOMETAX_ID                                                ) + // 7      【제출자】홈택스ID
					makeStr("X",   4, "9000"                                                    ) + // 8      【제출자】세무프로그램코드
					makeStr("X",   4, strIncYy                                                  ) + // 9      【귀속연도】귀속연도
					makeStr("X",  10, BUSI_REG_NO                                               ) + // 10     【원천징수의무자】④사업자등록번호
					makeStr("X",  40, CORP_NM                                                   ) + // 11     【원천징수의무자】③상호
					makeStr("X",  13, getStrInMap(map_M, "EMP_RES_NO"                       )   ) + // 12     【소득자】②소득자주민등록번호
					makeStr("9",   1, getStrInMap(map_M, "EMP_HOME_CLS"                     )   ) + // 13     【소득자】내･외국인코드
					makeStr("X",  30, getStrInMap(map_M, "EMP_NM"                           )   ) + // 14     【소득자】①성명
					makeStr("X",  10, getStrInMap(map_M, "BUSI_REG_NO"                      )   ) + // 15     【지급처】⑦지급처사업자등록번호
					makeStr("X",  40, getStrInMap(map_M, "MED_COP"                          )   ) + // 16     【지급처】⑧지급처상호
					makeStr("X",   1, getStrInMap(map_M, "MED_CLS"                          )   ) + // 17     【지급처】⑨의료증빙코드
					makeStr("9",   5, getStrInMap(map_M, "CNT1"                             )   ) + // 18     【지급명세】⑩건수
					makeStr("9",  11, getStrInMap(map_M, "MED_AMT"                          )   ) + // 19     【지급명세】⑪금액
					makeStr("X",   1, getStrInMap(map_M, "CA_YN"                            )   ) + // 20     【지급명세】⑫미숙아･선천성이상아해당여부
					makeStr("X",   1, getStrInMap(map_M, "ISA_YN"                           )   ) + // 21     【지급명세】⑬난임시술비해당여부
					makeStr("X",  13, getStrInMap(map_M, "RES_NO"                           )   ) + // 22     【의료비공제대상자】⑤주민등록번호
					makeStr("9",   1, getStrInMap(map_M, "HOME_CLS"                         )   ) + // 23     【의료비공제대상자】내･외국인코드
					makeStr("9",   1, getStrInMap(map_M, "DATA_CLS"                         )   ) + // 24     【의료비공제대상자】⑥본인등해당여부
					makeStr("9",   1, "1"                                                       ) + // 25     【의료비공제대상자】제출대상기간코드

				"\n"
			);
		}

		bw.close();

		return write(mode+"▥"+cmd+"▥"+fileNm+"▥"+msg);
	}




	//---------------------------------------------------------------------------------------------------------------
	// 공통모듈
	//---------------------------------------------------------------------------------------------------------------

	//map check
	private void check_wrk_map(String CNTC,String CALC_NO,String NM,Map map) throws Exception{
		String Rtype = (String) map.get("RECORD_TYPE");
		if      ("A".equals(Rtype)){

		}else if("B".equals(Rtype)){

		}else if("CM".equals(Rtype)){
			System.out.println(CNTC + ". " + "C RECORD : " + CALC_NO + " " + NM);

		}else if("CC".equals(Rtype)){

		}else if("D".equals(Rtype)){
			System.out.println("D");

		}else if("E".equals(Rtype)){
			System.out.println("E");

		}else if("F".equals(Rtype)){
			System.out.println("F");

		}else if("G".equals(Rtype)){
			System.out.println("G");

		}else if("H".equals(Rtype)){
			System.out.println("H");

		}else if("I".equals(Rtype)){
			System.out.println("I");

			/*
			String GIFT_CLS		  = String.valueOf(getStrInMap(map,"GIFT_CLS"));
			int GIFT_TOT_AMT 	  = Integer.parseInt(String.valueOf(map.get("GIFT_TOT_AMT")));
			int GIFT_ENC_RQST_AMT = Integer.parseInt(String.valueOf(map.get("GIFT_ENC_RQST_AMT")));
			System.out.println(
				String.format("기부코드: %s, 기부금: %d, 기부장려금: %d", GIFT_CLS, GIFT_TOT_AMT, GIFT_ENC_RQST_AMT)
			);
			*/

		}else{
			throw new Exception("type 값 오류입니다(" + Rtype + ")\n연말정산에 연락 바랍니다.");
		}
		return;
	}

	//파일 오픈
	private Writer openFile(String fileNm) throws Exception{
		String dir    = CommonUtil.getParameterValue("component.upload.directory");
		File f        = new File(dir);
		if (!f.exists()) { f.mkdirs(); }
	    String fileName  = dir + "/" + fileNm;
	    FileOutputStream bw_file = new FileOutputStream(fileName);
	    Writer bw                = new OutputStreamWriter(bw_file,"EUC-KR");
	    return bw;
	}

	//문자열 생성
	public String makeStr(String strAlign, int iByte, String strValue) throws Exception{

		if (!"X".equals(strAlign) && !"9".equals(strAlign) ) {
			throw new Exception("type 값 오류입니다(" + strAlign + ")\n연말정산에 연락 바랍니다.");
		}
		if(iByte <= 0){
			throw new Exception("길이 값 오류입니다(" + iByte + ")\n연말정산에 연락 바랍니다.");
		}
		if(strValue == null){
			throw new Exception("['" + strAlign + "' ," + iByte +"] 필드값이 null입니다. \n필드명 오류 또는 필드에 null값이 있습니다.\n연말정산에 연락 바랍니다.");
		}

		String strAdd   = null;
		String s_rtnVal = null;
		if ("X".equals(strAlign) ) {
			strAdd = CommonUtil.fill(" ", iByte);
			s_rtnVal = CommonUtil.isNull(strValue)? CommonUtil.leftBytes(strAdd, iByte)  : CommonUtil.leftBytes(strValue + strAdd, iByte);
		} else if ("9".equals(strAlign) ) {
			strAdd = CommonUtil.fill("0", iByte);
			s_rtnVal = CommonUtil.isNull(strValue)? CommonUtil.rightBytes(strAdd, iByte) : CommonUtil.rightBytes(strAdd + strValue, iByte);
		} else {
			return null;
		}

		if( "Error" == s_rtnVal && "Error".equals(s_rtnVal)) { System.out.println("변환도중 에러가 발생하였습니다.");}

		return s_rtnVal;
	}

	//key로 map에서 값 가져오기
	public String getStrInMap(Map map, String str) throws Exception {
		boolean b = map.containsKey(str);
		if( !b ) {
			System.out.println("\n" + ">> not exist map search KEY(getStrInMap() method). key-name: '" + str + "'<<" + "\n");
			throw new Exception("map에 없는 key입니다. \n소스를 확인하세요. \n(map-key명: '" + str + "')");
		}
		Object val = map.get(str);
		if ( val == null ) {
			System.out.println("\n" + ">> ERROR: map value is null(getStrInMap() method). field-name: '" + str + "'<<" + "\n");
			throw new Exception("검색한 필드 값이 null이어서 작업을 종료합니다.\n연말정산에 연락 바랍니다.\n(검색필드명: '" + str + "')");
		}
		return val.toString();
	}

	//바이트로 자르기
	/*
	protected String subByteData(String str, int lenth){
		if(str == null || str.trim().equals("")){ return ""; }	// 널(null) or "" 값 체크
		if(str.getBytes().length <= lenth){ return str;	}		// 이미 작은 경우 오리널 반환
		byte[] oldByte = str.getBytes();
		byte[] newByte = new byte[lenth];
		int start = 0;
		for(int j = 0 ; start < lenth ; j++){
			if(oldByte[j] >= 0 && oldByte[j] <= 127){		// 아스키 코드 0 ~ 127까지
				newByte[start] = oldByte[j];
				start++;
			}else if(oldByte[j] < 0 && start+1 < lenth){	// 2바이트 글자인데.. 공간이 남아있다. -> 다음꺼 까지 써라
				newByte[start] = oldByte[j];
				newByte[++start] = oldByte[++j];
		   		start++;
			}else if(oldByte[j] < 0 && start+1 >= lenth){	// 2바이트 인데.. 공간이 없다. -> 걍 너도 가라..
				j++;
				start=start+2;
			}else{                                          // 너는 정체는? 일딴 쓰고 가라...
				newByte[start] = oldByte[j];
				start++;
			}
		}
		return new String(newByte).trim();
	}
	*/


}
