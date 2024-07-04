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

def elapsed(original_func):
    @functools.wraps(original_func)
    def wrapper(*args, **kwargs):
        start = time.time()
        result = original_func(*args, **kwargs)
        end = time.time()
        print("수행시간: %f 초" % (end - start))
        return result
    return wrapper

def get_config_file_info(file_name):
    import configparser
    config = configparser.ConfigParser()
    config.read('hometax.ini', encoding='utf-8')
    return config['medium'][file_name]

##########################################################################

@elapsed
def checking_errors(used_range):
    print("checking... ", end='')
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

@elapsed
def insert_or_delete_row(ws, used_range):
    print("modifying...", end='')
    for i, row in enumerate(used_range, start=1):
        if i == 1: continue  # 제목줄이면 skip
        cmd = row[E]
        # 수정 값 I이면 자바쪽에 한줄을 추가
        # 수정 값 D이면 국세청쪽에 한줄을 추가(자바쪽을 삭제하는 효과)
        # 나머지는 모두 pass
        if cmd is None:
            pass
        else:
            cmd = cmd.upper()
            if cmd == "I":
                ws.Range(f"F{i}:G{i}").Insert()
                ws.Range(f"E{i}").Value = "(Done)" + cmd
            elif cmd == "D":
                ws.Range(f"A{i}:D{i}").Insert()
                ws.Range(f"E{i}").Value = "(Done)" + cmd
            elif cmd == "M":
                pass
            else:
                pass
                # ws.Range(f"E{i}").Font.ColorIndex = 3
                # ws.Cells(5, 15).Font.StrikeThrough = True
    return ws.UsedRange()

@elapsed
def highlighting_errors_in_line(ws, used_range):
    print("marking...  ", end='')
    l = len(used_range)
    ws.Range(f"F2:G{l}").Interior.ColorIndex = None
    ws.Range(f"F2:G{l}").Font.ColorIndex = 1

    tax_row_byte = java_row_byte = 0
    for i, row in enumerate(used_range, start=1):
        print(f"\rmarking... {'▒' * int(i/10)} ", end='')
        if i == 1: continue  # 제목줄이면 skip
        if not any(row): continue  # 전부 None이면 skip

      # A  B  C     D   E  F             G
        _, _, 항목, 값, _, 자바_in_java, 항목_in_java, *_ = row

        if not 항목:  # 항목 in 국세청row = [빈칸] 이면
            if not 자바_in_java:  # 자바코드 in 자바row = [빈칸] 이면
                pass
            else:
                ws.Range(f"F{i}").Font.ColorIndex = 15
        else:  # 항목 in 국세청row = [값]이 있으면c
            # 국세청 값의 누적
            m = tax_pat.search(값)
            tax_row_byte += int(m.group(1))
            if not 자바_in_java:  # 자바코드 in 자바row = [빈칸] 이면
                ws.Range(f"F{i}").Interior.ColorIndex = 40
            else:  # 자바코드 in 자바row = [값]이 있으면
                m = java_pat.search(자바_in_java)
                값_in_java = m.group(1) + "(" + m.group(2) + ")"
                # 자바 값의 누적
                java_row_byte += int(m.group(2))
                if 값 != 값_in_java:
                    ws.Range(f"F{i}").Interior.ColorIndex = 45
                if 항목 != 항목_in_java:
                    ws.Range(f"G{i}").Font.ColorIndex = 46
    return tax_row_byte, java_row_byte

def main():
    print("""
=====================================================================
 국세청 자료와 자바 소스를 비교하여 차이점(길이, 항목)을 보여줍니다
 자바 라인 삭제는 'D', 라인 추가는 'I', 라인 수정은 'M'을 입력합니다
 국세청 자료의 byte와 자바 소스의 byte가 일치할 때까지 수정하세요
=====================================================================
    """)

    # excel 작업 파일을 오픈
    file_name = get_config_file_info('excel_file')
    excel = win32com.client.Dispatch("Excel.Application")
    excel.Visible = True
    excel.DisplayAlerts = True  # False이면 sheet에 값이 있을때 sheet를 삭제하면 prompt가 나오는게 일반적인데 prompt 없이 삭제하라고 설정
    BOOK = excel.Workbooks.Open(file_name)

    # sheet 명 입력할 때 맞는 이름인지 check하기 위해 존재하는 sheet 명을 저장
    rcd_sheet_names = [sh.Name for sh in BOOK.Sheets if sh.Name in 'ABCDEFGHIJKLMNOPQRSTUVWXYZ']

    while True:
        # 검증할 sheet를 선택
        rcd_sheet = input(f"검증할 sheet 이름{rcd_sheet_names}을 입력하세요(취소 Q): ").upper()
        if rcd_sheet == 'Q': return
        if rcd_sheet is None or rcd_sheet not in rcd_sheet_names: continue

        # 선택한 sheet의 모든 값을 '시트_데이터'에 저장
        ws = BOOK.Sheets(rcd_sheet)
        ws.Select()
        시트_데이터 = ws.UsedRange()

        try:
            # 먼저 오류를 찾는다
            checking_errors(used_range=시트_데이터)
            # 수정 칸에 입력한 I, D를 처리한다
            시트_데이터 = insert_or_delete_row(ws, used_range=시트_데이터)
            # 국세청 자료와 불일치(타입, 길이, 항목)하는 자바코드를 표시한다
            tax_row_byte, java_row_byte = highlighting_errors_in_line(ws, used_range=시트_데이터)

            ws.Range(f"D1").Value = f"값({int(tax_row_byte)})"
            ws.Range(f"F1").Value = f"자바({int(java_row_byte)})"

            print(f'\n국세청 {tax_row_byte} byte, 자바 {java_row_byte} byte, 차이 {tax_row_byte - java_row_byte} btye')
        except MyError as e:
            print(e)
            return
        break

##########################################################################

if __name__ == "__main__":
    main()