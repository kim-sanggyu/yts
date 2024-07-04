import win32com.client
import re
import functools
import time

A = 0; B = 1; C = 2; D = 3; E = 4; F = 5; G = 6; H = 7; I = 8; J = 9; K = 10; M = 11; N = 12
# makeStr("9", 1, getStrInMap(map_C_main, "HABT_CLS")) + // C7     【소득자(근로자)】거주구분코드
java_pat = re.compile(r'\("([9xX])",\s*(\d+),.*\)$')
tax_pat = re.compile(r'\(([\d]+)\)')

##########################################################################

class MyError(Exception):
    pass

def get_config_file_info(file_name):
    import configparser
    config = configparser.ConfigParser()
    config.read('hometax.ini', encoding='utf-8')
    return config['medium'][file_name]

def set_cells_property(new_ws, font_size=8, font_name="D2Coding", interior_colorindex=None, font_colorindex=1):
    all_range = new_ws.Range(new_ws.Cells(1, 1), new_ws.Cells(new_ws.Rows.Count, new_ws.Columns.Count))
    all_range.Font.Size = font_size
    all_range.Font.Name = font_name
    all_range.Interior.ColorIndex = interior_colorindex
    all_range.Font.ColorIndex = font_colorindex

def set_title_property(new_ws, interior_colorindex=None, **cells):
    for key, value in cells.items():
        new_ws.Range(key).Value = value
        new_ws.Range(key).Interior.ColorIndex = interior_colorindex

def autoFitSheet(sheet):
    """Freezes the top line of the given sheet."""
    sheet.Columns.AutoFit()
    sheet.Rows.AutoFit()

def freezeTopLine(excel, sheet):
    """Freezes the top line of the given sheet."""
    sheet.Activate()
    excel.Range("A2").Select()
    excel.ActiveWindow.FreezePanes = True

##########################################################################

def checking_errors(used_range):
    for i, row in enumerate(used_range, start=1):
        if i == 1: continue  # 제목줄이면 skip
        if not any(row): continue  # 전부 None이면 skip

        # 국세청 row 4칸 모두 빈칸 이던지 모두 빈칸이 아니든지 해야함
        # 자바소스 row 2칸 모두 빈칸 이던지 모두 빈칸이 아니든지 해야함
        # 자바소스내 타입과 길이를 정규식에 의해 구할 수 있어야 함
        if len({bool(x) for x in row[A:D+1]}) > 1:
            raise MyError(f"\nERROR: [국세청] row에 빈 cell이 있습니다.(row no = {i})")
        if len({bool(x) for x in row[F:G+1]}) > 1:
            raise MyError(f"\nERROR: [자바코드] row에 빈 cell이 있습니다.(row no = {i})")
        if row[F]:
            m = java_pat.search(row[F])
            if not m:
                raise MyError(f"\nERROR: 정규식 분석 오류입니다(아래 자바코드)\n{i} row -> {row[F]}")

def making_new_java_code(new_ws, used_range):

    set_cells_property(new_ws)
    set_title_property(new_ws, interior_colorindex=22, A1="국세청누적", B1="자바누적", C1="자바코드")

    line_no = 1
    tax_row_byte =  java_row_byte = diff_cnt = 0

    for i, row in enumerate(used_range, start=1):

        if i == 1: continue
        # if not any(row): continue  # 전부 None이면 skip
        if not any(row[A:D]): continue  # 국세청 row가 전부 None이면 skip

        구분, 코드, 항목, 값, _, 자바_in_java, *_ = row
        tax_row_byte  += int(tax_pat.search(값).group(1))
        if 자바_in_java:
            java_row_byte += int(java_pat.search(자바_in_java).group(2))
            합친자바코드 = 자바_in_java + " + // " + 코드 + " " * (6 - len(코드.encode(encoding='EUC-KR'))) + 구분 + 항목
        else:
            합친자바코드 = None

        line_no += 1
        new_ws.Range(f"A{line_no}").Value = tax_row_byte
        new_ws.Range(f"B{line_no}").Value = java_row_byte
        new_ws.Range(f"C{line_no}").Value = 합친자바코드

        if tax_row_byte != java_row_byte:
            diff_cnt += 1
            new_ws.Range(f"A{line_no}:B{line_no}").Interior.ColorIndex = 44

    new_ws.Range(f"C{line_no+1}").Value = r'"\n"'
    new_ws.Rows.AutoFit()

    return tax_row_byte, java_row_byte, diff_cnt


def main(NEW_SHEET_NAME):
    print("""
========================================================================
 올해 국세청 자료와 작년 자바소스를 기반으로 올해 자바소스를 생성합니다
 생성된 자바소스는 [New_자바소스(*)] sheet에 생성됩니다
========================================================================
    """)

    # excel 작업 파일을 오픈
    file_name = get_config_file_info('excel_file')
    excel = win32com.client.Dispatch("Excel.Application")
    excel.Visible = True
    excel.DisplayAlerts = False  # False이면 sheet에 값이 있을때 sheet를 삭제하면 prompt가 나오는게 일반적인데 prompt 없이 삭제하라고 설정
    BOOK = excel.Workbooks.Open(file_name)

    # excel의 sheets 정보 확보
    all_sheet_names = [sh.Name for sh in BOOK.Sheets]
    rcd_sheet_names = [sh.Name for sh in BOOK.Sheets if sh.Name in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ']

    while True:
        # java 소스를 생성할 record sheet를 선택
        rcd_sheet = input(f"자바소스를 생성할 sheet 이름{rcd_sheet_names}을 입력하세요(취소 Q): ").upper()
        if rcd_sheet == 'Q': return
        if rcd_sheet is None or rcd_sheet not in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' or rcd_sheet not in rcd_sheet_names:
            continue

        # 선택한 sheet의 모든 값을 '시트_데이터'에 저장
        ws = BOOK.Sheets(rcd_sheet)
        시트_데이터 = ws.UsedRange()

        # java 소스를 담을 sheet를 생성(기존 있으면 삭제하고)
        NEW_SHEET_NAME = NEW_SHEET_NAME + "(" + rcd_sheet + ")"
        if NEW_SHEET_NAME in all_sheet_names:
            BOOK.Sheets(NEW_SHEET_NAME).Delete()
        new_ws = BOOK.Sheets.Add(Before=None, After=BOOK.Sheets(BOOK.Sheets.Count))  # 신규 sheet가 맨 뒤에 생성되도록
        new_ws.Name = NEW_SHEET_NAME

        # new_ws에 java 소스를 생성
        try:
            checking_errors(used_range=시트_데이터)
            tax_row_byte, java_row_byte, diff_cnt = making_new_java_code(new_ws, used_range=시트_데이터)
            print(f'\n국세청 {tax_row_byte} byte, 자바 {java_row_byte} byte, 차이 {tax_row_byte - java_row_byte} btye')
            if diff_cnt != 0:
                print(f'ERROR: 길이가 상이한 record가 {diff_cnt} 건 있습니다.')
        except MyError as e:
            print(e)
            return
        break

    # sheet 보기 좋게 편집하고 sheet 선택
    ws = BOOK.Sheets(NEW_SHEET_NAME)
    autoFitSheet(ws)
    freezeTopLine(excel, ws)
    ws.Activate()

##########################################################################

if __name__ == "__main__":
    NEW_SHEET_NAME = 'new_자바'
    main(NEW_SHEET_NAME)