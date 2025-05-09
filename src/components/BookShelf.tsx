import React from 'react';

const BookShelf: React.FC = () => {
  // ... existing code ...
  
  // 修改排序逻辑，按照添加时间倒序排列
  const sortedBooks = [...books].sort((a, b) => 
    new Date(b.addedDate).getTime() - new Date(a.addedDate).getTime()
  );
  
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-2 p-4">
      {sortedBooks.map((book) => (
        <div key={book.id} className="relative group">
          <img
            src={book.cover}
            alt={book.title}
            className="w-4/5 h-auto object-cover rounded-lg shadow-md transition-transform duration-300 group-hover:scale-105 mx-auto"
          />
          <div className="mt-2 text-center">
            <h3 className="text-sm font-semibold">{book.title}</h3>
            <p className="text-xs text-gray-600">{book.author}</p>
            <p className="text-xs text-gray-500">导入于: {new Date(book.addedDate).toLocaleDateString()}</p>
          </div>
        </div>
      ))}
    </div>
  );
};

export default BookShelf; 