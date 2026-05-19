import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'

export async function exportToPDF(elementId, filename = 'export.pdf') {
  const element = document.getElementById(elementId)
  if (!element) {
    console.error('找不到要导出的元素:', elementId)
    return
  }

  try {
    // 获取原始的滚动位置
    const originalScrollTop = window.pageYOffset
    const originalScrollLeft = window.pageXOffset
    
    // 临时调整滚动以确保完整捕获
    window.scrollTo(0, 0)
    
    const canvas = await html2canvas(element, {
      scale: 2,
      useCORS: true,
      allowTaint: true,
      backgroundColor: '#ffffff',
      logging: false,
      scrollX: 0,
      scrollY: 0
    })
    
    // 恢复原始滚动位置
    window.scrollTo(originalScrollLeft, originalScrollTop)

    const imgWidth = 210 // A4宽度，单位mm
    const pageHeight = 297 // A4高度
    const imgHeight = (canvas.height * imgWidth) / canvas.width
    let heightLeft = imgHeight
    let position = 0

    const pdf = new jsPDF('p', 'mm', 'a4')
    let firstPage = true

    while (heightLeft > 0) {
      if (!firstPage) {
        pdf.addPage()
      }
      
      pdf.addImage(
        canvas,
        'JPEG',
        0,
        position,
        imgWidth,
        imgHeight
      )
      
      heightLeft -= pageHeight
      position = heightLeft
      firstPage = false
    }

    pdf.save(filename)
    return true
  } catch (error) {
    console.error('导出PDF失败:', error)
    throw error
  }
}

export async function exportToPDFMultiPage(elementId, filename = 'export.pdf') {
  const element = document.getElementById(elementId)
  if (!element) {
    console.error('找不到要导出的元素:', elementId)
    return
  }

  try {
    const originalScrollTop = window.pageYOffset
    const originalScrollLeft = window.pageXOffset
    window.scrollTo(0, 0)

    const canvas = await html2canvas(element, {
      scale: 2,
      useCORS: true,
      allowTaint: true,
      backgroundColor: '#ffffff',
      logging: false,
      scrollX: 0,
      scrollY: 0
    })

    window.scrollTo(originalScrollLeft, originalScrollTop)

    const imgWidth = 210 // A4宽度
    const pageHeight = 297 // A4高度
    const imgHeight = (canvas.height * imgWidth) / canvas.width
    let heightLeft = imgHeight
    let position = 0

    const pdf = new jsPDF('p', 'mm', 'a4')

    if (heightLeft <= pageHeight) {
      pdf.addImage(canvas, 'JPEG', 0, 0, imgWidth, imgHeight)
    } else {
      while (heightLeft > 0) {
        pdf.addImage(
          canvas,
          'JPEG',
          0,
          position,
          imgWidth,
          imgHeight
        )
        heightLeft -= pageHeight
        if (heightLeft > 0) {
          position = heightLeft
          pdf.addPage()
        }
      }
    }

    pdf.save(filename)
    return true
  } catch (error) {
    console.error('导出PDF失败:', error)
    throw error
  }
}
