import win32com.client
import re
import time

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

def parsing_java_code(java_file):
    java_lines = []
    with open(java_file, encoding="utf-8") as f:
        lines = f.readlines()
        for line_no, line in enumerate(lines, start=1):
            line = line.strip()
            if line:
                rcd_type = is_layout_code(line_no)
                if rcd_type:
                    rcd = []
                    line = re.split(r'//|【|】', line)
                    코드 = rcd_type
                    번호 = line_no
                    자바 = line[0].replace("+", "").strip()
                    항목 = line[3].strip()
                    rcd.append(번호)
                    rcd.append(코드)
                    rcd.append(자바)
                    rcd.append(항목)
                    java_lines.append(rcd)
    return java_lines

def is_layout_code(line_no):
    java_lines = (('A', 228, 245),
                  ('B', 291, 309),
                  ('C', 388, 594),
                  ('D', 623, 699),
                  ('E', 721, 727), ('E', 733, 794), ('E', 807, 809),
                  ('F', 832, 838), ('F', 846, 856), ('F', 872, 874),
                  ('G', 896, 903), ('G', 896, 903), ('G', 957, 959),
                  ('H', 974, 993),
                  ('I', 1007, 1028),)
    for java_line in java_lines:
        rcd_type, start_line, end_line = java_line
        if start_line <= line_no < end_line:
            return rcd_type
    return False

def create_java_sheet(BOOK, NEW_SHEET_NAME, java_lines):
    ws = BOOK.Sheets.Add(Before=None, After=BOOK.Sheets(BOOK.Sheets.Count))  # 신규 sheet가 맨 뒤에 생성되도록
    ws.Name = NEW_SHEET_NAME

    set_cells_property(ws)
    set_title_property(ws, interior_colorindex=6, A1="번호", B1="코드", C1="자바", D1="항목")
    ws.Range("A1:D1").Interior.ColorIndex = 6
    row_cnt = 1

    for data in java_lines:
        row_cnt += 1
        ws.Range(f"A{row_cnt}").Value = data[0]
        ws.Range(f"B{row_cnt}").Value = data[1]
        ws.Range(f"C{row_cnt}").Value = data[2]
        ws.Range(f"D{row_cnt}").Value = data[3]

def main(NEW_SHEET_NAME):
    # excel 작업 파일을 오픈
    file_name = get_config_file_info('excel_file')
    excel = win32com.client.Dispatch("Excel.Application")
    excel.Visible = True
    excel.DisplayAlerts = False  # False: 값 있는 sheet 삭제 시 prompt 생략, True: prompt 나오게(기본)
    BOOK = excel.Workbooks.Open(file_name)

    # '국세청' 이외 sheet는 모두 삭제
    for sh in BOOK.Sheets:
        if sh.Name not in ['국세청']:
            BOOK.Sheets(sh.Name).Delete()

    # 자바소스를 읽어 '자바_데이터'에 저장
    java_file = get_config_file_info('java_file')
    자바_데이터 = parsing_java_code(java_file)

    # '자바_데이터'를 읽어 '자바' sheet를 생성
    create_java_sheet(BOOK, NEW_SHEET_NAME, java_lines=자바_데이터)

    # sheet 보기 좋게 편집하고 sheet 선택
    ws = BOOK.Sheets(NEW_SHEET_NAME)
    autoFitSheet(ws)
    ############################
    freezeTopLine(excel, ws)

##########################################################################

if __name__ == "__main__":
    NEW_SHEET_NAME = '자바'
    main(NEW_SHEET_NAME)
