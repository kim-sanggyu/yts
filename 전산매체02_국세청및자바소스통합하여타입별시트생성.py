import win32com.client
import time

A = 0; B = 1; C = 2; D = 3; E = 4; F = 5; G = 6; H = 7; I = 8; J = 9; K = 10; M = 11; N = 12
ERROR_MSG = ''

##########################################################################

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

def get_sheet_data(BOOK, sheet_name):
    return BOOK.Sheets(sheet_name).UsedRange()

def get_code_set(used_range):
    code_set = set()
    for row in used_range:
        코드 = row[B]
        if 코드 is None: continue
        if 코드[0].upper() not in 'ABCDEFGHIJ': continue
        if 코드[0] in 'abcdefghij':
            ERROR_MSG = f"오류발생: 레코드 타입 코드('{코드[0]}')가 잘못되었습니다."
            raise Exception(ERROR_MSG)
        code_set.add(코드[0])
    sorted_code_set = sorted(code_set)
    print(f"▶생성할 시트: {sorted_code_set}")
    return sorted_code_set

def create_hometax_layout_data(BOOK, used_range, sorted_code_set):
    print("▶국세청 전산매체 자료를 sheet로 분리합니다.")
    for rcd_type in sorted_code_set:
        ws = BOOK.Sheets.Add(Before=None, After=BOOK.Sheets(BOOK.Sheets.Count))  # 신규 sheet가 맨 뒤에 생성되도록
        ws.Name = rcd_type

        set_cells_property(ws)
        set_title_property(ws, interior_colorindex=20, A1="구분", B1="코드", C1="항목", D1="값")
        ws.Range("D:D").HorizontalAlignment = -4108

        line_no = 1
        for tax_data in used_range:
            if not any(tax_data): continue

            if tax_data[B].startswith(rcd_type):
                line_no += 1
                ws.Range(f"A{line_no}").Value = tax_data[A].replace(" ", "")
                ws.Range(f"B{line_no}").Value = tax_data[B].replace(" ", "")
                ws.Range(f"C{line_no}").Value = tax_data[C].replace(" ", "")
                ws.Range(f"D{line_no}").Value = tax_data[D].replace(" ", "")

def create_java_layout_data(BOOK, used_range, sorted_code_set):
    print("▶자바 전산매체 코드를 sheet로 업데이트합니다.")
    for rcd_type in sorted_code_set:
        ws = BOOK.Sheets(rcd_type)

        set_title_property(ws, interior_colorindex=20, F1="자바", G1="항목")

        line_no = 1
        for java_data in used_range:
            if not any(java_data): continue

            if java_data[B] == rcd_type:
                line_no += 1
                ws.Range(f"F{line_no}").Value = java_data[C].strip()
                ws.Range(f"G{line_no}").Value = java_data[D].replace(" ", "")

def create_entry_layout(BOOK, sorted_code_set):
    for rcd_type in sorted_code_set:
        ws = BOOK.Sheets(rcd_type)
        set_title_property(ws, interior_colorindex=44, E1="수정(D,I,M)")
        ws.Range("E:E").HorizontalAlignment = -4108

def main():
    # excel 작업 파일을 오픈
    file_name = get_config_file_info('excel_file')
    excel = win32com.client.Dispatch("Excel.Application")
    excel.Visible = True
    excel.DisplayAlerts = False  # False: 값 있는 sheet 삭제 시 prompt 생략, True: prompt 나오게(기본)
    BOOK = excel.Workbooks.Open(file_name)

    # 국세청, 자바 이외 sheet는 모두 삭제
    for sh in BOOK.Sheets:
        if sh.Name not in ['국세청', '자바']:
            BOOK.Sheets(sh.Name).Delete()

    # 1 ###########################################################
    # [국세청] sheet에 있는 데이터를 튜플(국세청_시트_데이터)로 저장
    # '국세청_시트_데이터'(국세청 sheet)를 기준으로 '레코드_종류' 생성
    # '레코드_종류'를 기준으로 새로운 sheet(C,D,E,F,...)를 생성
    국세청_시트_데이터 = get_sheet_data(BOOK, sheet_name="국세청")
    레코드_종류 = get_code_set(used_range=국세청_시트_데이터)
    create_hometax_layout_data(BOOK, used_range=국세청_시트_데이터, sorted_code_set=레코드_종류)

    # 2 ###########################################################
    # [자바] sheet에 있는 데이터를 튜플(자바_시트_데이터)로 저장
    # '레코드_종류'를 기준으로 생성된 sheet에 '자바_시트_데이터'를 옮겨 놓음
    자바_시트_데이터 = get_sheet_data(BOOK, sheet_name="자바")
    create_java_layout_data(BOOK, used_range=자바_시트_데이터, sorted_code_set=레코드_종류)

    # 3 ###########################################################
    # 변경 명령 입력 column 생성
    create_entry_layout(BOOK, sorted_code_set=레코드_종류)

    for rcd_type in sorted(레코드_종류, reverse=True):
        # time.sleep(3)
        ws = BOOK.Sheets(rcd_type)
        autoFitSheet(ws)
        freezeTopLine(excel, ws)

    # ws = BOOK.Sheets("C")
    # ws.Activate()

##########################################################################

if __name__ == "__main__":
    main()