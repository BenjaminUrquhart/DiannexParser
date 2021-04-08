; save.ruined1 (flags=[rh1_save=0]):
pushi 1
setvarglb global.ui_force_side
callext _saveSound 0
pop 
pushvarloc 0
pushi 1
add
setvarloc 0
pushs "* Bepis "
pushvarloc 0
add
textrun 
pushi -1
setvarglb global.ui_force_side
callext _doSave 0
pop 
freeloc 0
exit 